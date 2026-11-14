# Project Doorstep: loan-application fraud check

A Spring Boot service that checks every new loan application against KYC, bureau and device signals. It looks across earlier applications in real time (Redis) and returns an explainable **PASS / REVIEW / REJECT** decision with reasons. Risky applications open an underwriter case. Applications arrive over REST or Kafka, and underwriters work the queue in a React screen with an audited, state-machine-governed workflow.

All data is synthetic. No real PANs, phone numbers or personal data are used.

## Architecture

```
 Partner channels ──► Kafka loan.applications.v1 ──► ApplicationEventListener ─┐
                                         │ (malformed / conflict / 3 failed retries)
                                         └──────────► loan.applications.v1.DLT  │
 Mobile app / DSA ──► POST /api/applications ──► ApplicationController ─────────┤
                                                                                ▼
                                                            ApplicationEvaluationService (one path, idempotent)
                                                              │ SignalCollector ──► SignalStore ──► Redis sorted sets
                                                              │ FraudEvaluator  ──► 6 FraudRule beans (config-driven)
                                                              │ JPA ──► PostgreSQL (Flyway): loan_application,
                                                              │                       rule_hit, fraud_case, case_audit
                                                              └ DecisionMadeEvent ─(after commit)─► Kafka loan.decisions.v1
 React review UI ──► GET /api/cases, /api/cases/{id}, /audit ; POST /api/cases/{id}/actions ──► CaseWorkflowService
```

| Package | Responsibility |
|---|---|
| `api` | REST controllers, request/response DTOs, error mapping (400 field errors, 404, 409) |
| `rules` | `FraudRule` implementations. These are pure functions of (application, cross-application signals). `FraudEvaluator` sums points and maps them to a decision |
| `signals` | `SignalStore` interface, Redis and in-memory implementations, and `SignalCollector` (windows anchored on an injected `Clock`) |
| `service` | Evaluation orchestration and idempotency, case queries, case workflow |
| `persistence` | JPA entities and repositories; the case state machine (`CaseAction`) |
| `ingest` | Kafka listener, error handler (DLT and retries), decision publisher |
| `generator` | Seeded synthetic data generator, `GeneratorCli` (REST), `ReplayCli` (Kafka) |
| `ui/` | React + TypeScript (Vite) underwriter screen |

## Design decisions

- **Rules are pure, and the Redis lookups happen once, before them.** `SignalCollector` reads the windows into a `CrossApplicationSignals` value, then each rule decides from that value alone. This makes every rule unit-testable without Redis or Spring.
- **Time windows use one sorted set per identifier, scored by `submittedAt` epoch millis.** Each read first trims everything older than the window (`ZREMRANGEBYSCORE`), then reads the remaining members (`ZRANGEBYSCORE`). "Now" comes from an injectable `Clock`. Keys also get a TTL equal to the window.
  - PAN: `signal:pan_identity:<sha256(pan)>`, with members `applicationId|dob|normalisedName`
  - Phone: `signal:phone_pans:<sha256(phone)>`, with members `sha256(pan)`
  - Device: `signal:device_pans:<sha256(deviceId)>`, with members `sha256(pan)`

  Because members are set elements, re-recording the same application is a no-op. Counts can't be double-incremented.
- **"Counting this one":** the current PAN hash is added to the set read from Redis before counting distinct PANs.
- **Configuration:** all points, windows, count limits and thresholds live under `fraud.*` in `application.yml` and bind to the `FraudProperties` record.
- **Masking:** full PAN and phone are stored only in `loan_application`, which is the system of record. Every response, rule reason, log line and Kafka decision event uses `Masking` (`******234F`, `******3210`). Redis holds only SHA-256 hashes.
- **Idempotency:** `loan_application.application_id` is the primary key, which is the database unique constraint. `payload_hash` (SHA-256 of canonical JSON) tells the two repeat cases apart:
  - Exact repeat: the stored result is returned and nothing is written (no rule hits, case, Redis update or decision event).
  - Same id with a different payload: a `ConflictException`, which becomes 409 over REST or goes to the DLT over Kafka.
  - The application row is inserted with `saveAndFlush` *before* Redis is touched, so a concurrent duplicate fails on the primary key first. The Kafka retry then sees an existing row and skips it.
- **Case workflow:** `CaseAction` encodes the allowed transitions (OPEN → APPROVED/REJECTED/ESCALATED, and ESCALATED → APPROVED/REJECTED). The status change and the `case_audit` insert happen in one transaction. `CaseAuditRepository` extends the bare `Repository` interface and exposes only `save` and `find`, so no update or delete path exists.

### Kafka offset-commit strategy

- `enable-auto-commit: false` and `ack-mode: record`. The container commits a record's offset only after the listener returns successfully, or after the error handler has published the record to `loan.applications.v1.DLT`.
- A crash mid-processing therefore means redelivery (at-least-once). The idempotency check above makes that safe.
- `MalformedEventException` (unparseable JSON, failed validation, key ≠ applicationId) and `ConflictException` are marked not-retryable, so they go straight to the DLT.
- Every other exception (for example the database being down) is retried 3 times with exponential backoff (200 ms, ×2), then dead-lettered.
- Messages are keyed by `applicationId`, so redeliveries of one application land on the same partition and are processed in order.

## How to run

Prerequisites: JDK 17+, Maven 3.9+, Node 18+, and Docker (for the live stack only).

```bash
cp .env.example .env                      # set DB_PASSWORD
docker compose up -d                      # PostgreSQL, Redis, Kafka (KRaft)
set -a; source .env; set +a
mvn spring-boot:run                       # service on :8080, Flyway migrates the schema
```

Post a sample application:

```bash
curl -s localhost:8080/api/applications -H 'Content-Type: application/json' -d '{
 "applicationId":"DEMO-1","applicantName":"Asha Verma","dob":"1990-05-17","pan":"ABCDE1234F",
 "phone":"9876543210","deviceId":"dev-1","requestedAmount":450000,"declaredMonthlyIncome":200000,
 "kyc":{"nameOnId":"Asha Verma","dobOnId":"1990-05-17","verified":true},
 "bureau":{"score":null,"historyMonths":2,"estimatedMonthlyIncome":30000},
 "submittedAt":"2026-11-14T06:00:00Z"}'
# -> decision REVIEW, score 35, ruleHits THIN_FILE_HIGH_AMOUNT + INCOME_INFLATED, caseId ...
curl -s 'localhost:8080/api/cases?status=OPEN'
```

To run without Redis, start with `--doorstep.signals.store=memory`. To run without Kafka, start with `--doorstep.kafka.enabled=false`.

### Data generator (Task 1)

```bash
scripts/generate.sh 500 http://localhost:8080
```

This generates 500 seeded applications and posts them to the API, then prints counts per decision and per rule. The seeded patterns are:
- a phone ring (8 PANs on one phone)
- a device ring (8 PANs on one device)
- a recycled PAN with changed names (5 applications)
- 30 inflated-income applicants
- 15 KYC name mismatches
- 15 thin-file, high-amount applicants
- clean applicants for the rest

### Kafka replay (Task 2)

```bash
docker compose down -v && docker compose up -d     # clean database and Redis
mvn spring-boot:run                                 # fresh service, so counters start at 0
scripts/replay.sh localhost:9092 http://localhost:8080
```

The replay publishes 1,000 new applications, 50 exact redeliveries, 10 conflicting redeliveries and 10 malformed messages. It then prints the service's own Micrometer counters and the p95 of the `doorstep.evaluation.latency` timer (consume → decision).

### Review UI (Task 3)

```bash
cd ui && npm install && npm run dev        # http://localhost:5173, proxies /api to :8080
```

- **Left, the queue:** filterable by status, highest score first (sorted by the API using the `(status, score desc)` index). It shows masked PAN and phone.
- **Right, the detail panel:** opens on clicking a case. It shows the masked identifiers, device, amount, each rule hit with its reason, and the audit history. It also has APPROVE / REJECT / ESCALATE buttons with an acting-user field and a comment box. When the API rejects an action (409 invalid transition, 400 short comment), its message is shown in red under the buttons.

## How to run the tests

```bash
mvn test                     # offline: H2 (PostgreSQL mode), in-memory SignalStore, @EmbeddedKafka
mvn test -Pintegration       # needs docker compose up: live Redis window test
cd ui && npm install && npm test
```

| Requirement | Test |
|---|---|
| Each rule: positive and negative cases | `FraudRulesTest` |
| PASS/REVIEW at exactly 30, REVIEW/REJECT at exactly 60 | `FraudEvaluatorTest` |
| 3rd PAN on a phone inside 30 days fires; not when older (fixed `Clock`) | `SignalWindowTest` |
| No full PAN or phone in `GET /api/cases` and `/api/cases/{id}` | `ApplicationApiTest` |
| A threshold change in config changes the decision | `ThresholdConfigTest` (`fraud.thresholds.review=20` turns a 20-point PASS into REVIEW) |
| Generator patterns produce cases | `GeneratorPipelineTest` |
| Same Kafka event twice → 1 case, 1 rule-hit set, 1 decision event; no full PAN in decisions | `KafkaIngestionTest` |
| Malformed and conflicting events → DLT | `KafkaIngestionTest` |
| Exact repeat leaves Redis signals unchanged; conflict detected | `IdempotencyTest` |
| Valid transition writes audit, 409 on final state, 400 on short comment | `CaseWorkflowApiTest` |
| UI renders queue with masked identifiers only | `ui/src/App.test.tsx` |

## Measured numbers

> Fill in after running against the live stack. The figures below must come from an actual run.

**Generator run (Task 1)**, `scripts/generate.sh 500`:

| Decision | Count |
|---|---|
| PASS | _tbd_ |
| REVIEW | _tbd_ |
| REJECT | _tbd_ |

| Rule | Hits |
|---|---|
| PHONE_SHARED | 6 (asserted in `GeneratorPipelineTest`) |
| DEVICE_SHARED | 6 (asserted) |
| PAN_REUSED_DIFFERENT_IDENTITY | 4 (asserted) |
| INCOME_INFLATED / KYC_MISMATCH / THIN_FILE_HIGH_AMOUNT | _tbd_ |

`GeneratorPipelineTest` also prints the full breakdown as a `Generator run:` line in the `mvn test` output.

**Replay run (Task 2)**, `scripts/replay.sh`: _tbd: paste output (evaluated, duplicates, DLT, cases, p95 ms)_

## What I would do next

- Fuzzy name matching (Jaro-Winkler) for `KYC_MISMATCH` and PAN reuse, to catch initials and transposed names.
- Atomic Redis read-and-record via a Lua script, to close the small race between two simultaneous applications on the same phone.
- Spring Security with UNDERWRITER / SENIOR_UNDERWRITER roles instead of the trusted `X-User` header.
- A database trigger that forbids UPDATE/DELETE on `case_audit`, as defence in depth.
- Testcontainers-based integration tests for PostgreSQL + Redis + Kafka under `-Pintegration`.
- An outbox table for decision events (today they're sent after commit, so a crash in between can drop one).
- A GitHub Actions workflow running `mvn test` and `npm test`.
