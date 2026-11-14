# Project Doorstep: fraud checks for loan applications

Doorstep checks every new loan application for fraud **before** money is paid out. It:

1. **Scores** each application against six fraud rules (KYC, bureau, device and cross-application signals).
2. **Looks across earlier applications** in real time, using Redis, to catch rings that reuse one phone, device or PAN.
3. **Decides** PASS / REVIEW / REJECT and explains every decision with human-readable reasons.
4. **Opens a case** for every REVIEW or REJECT, which an underwriter works in a web screen with an audited workflow.

Applications arrive through a **REST API** (mobile app or DSA partners) or a **Kafka topic** (partner channels). Both paths use the same evaluation code.

> All data in this project is synthetic. No real PANs, phone numbers or personal data are used.

---

## Contents
1. [Quick start](#1-quick-start)
2. [Try it](#2-try-it)
3. [Run the tests](#3-run-the-tests)
4. [Generator and replay scripts](#4-generator-and-replay-scripts)
5. [Underwriter review screen](#5-underwriter-review-screen)
6. [Configuration](#6-configuration)
7. [API reference](#7-api-reference)
8. [How it works](#8-how-it-works)
9. [Measured numbers](#9-measured-numbers)
10. [Project layout](#10-project-layout)
11. [Troubleshooting](#11-troubleshooting)
12. [What I would do next](#12-what-i-would-do-next)

---

## 1. Quick start

### Prerequisites
| Tool | Version | Check with |
|---|---|---|
| Java (JDK) | 17 or newer | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 18+ | `node -v` |
| Docker Desktop | any recent version, **running** | `docker -v` |

### Steps
```bash
# 1. Get the code
git clone https://github.com/ashima0102/project-doorstep.git
cd project-doorstep

# 2. Create your local settings file (git-ignored; set any password you like)
cp .env.example .env

# 3. Start PostgreSQL, Redis and Kafka
docker compose up -d
docker compose ps            # all three should say "running"

# 4. Start the service (Flyway creates the database tables on first start)
set -a; source .env; set +a
mvn spring-boot:run          # wait for "Started DoorstepApplication"
```
The API is now on **http://localhost:8080**.

To stop everything: press `Ctrl+C` in the service window, then run `docker compose down`. Add `-v` to also wipe the data.

---

## 2. Try it

Post one application, in a second terminal:
```bash
curl -s localhost:8080/api/applications -H 'Content-Type: application/json' -d '{
  "applicationId": "DEMO-1",
  "applicantName": "Asha Verma",
  "dob": "1990-05-17",
  "pan": "ABCDE1234F",
  "phone": "9876543210",
  "deviceId": "dev-1",
  "requestedAmount": 450000,
  "declaredMonthlyIncome": 200000,
  "kyc":    { "nameOnId": "Asha Verma", "dobOnId": "1990-05-17", "verified": true },
  "bureau": { "score": null, "historyMonths": 2, "estimatedMonthlyIncome": 30000 },
  "submittedAt": "2026-11-14T06:00:00Z"
}'
```
Expected response (shortened):
```json
{ "applicationId": "DEMO-1", "decision": "REVIEW", "score": 35,
  "ruleHits": [
    { "ruleId": "INCOME_INFLATED", "points": 20, "reason": "Declared monthly income 200000 is more than 3x the bureau estimate 30000" },
    { "ruleId": "THIN_FILE_HIGH_AMOUNT", "points": 15, "reason": "Thin bureau file (score none, 2 months history) with requested amount 450000 above 300000" } ],
  "caseId": "…" }
```
- **Same request again:** sending the exact same request returns the **same** result and caseId. Repeats are idempotent.
- **Invalid data:** something like `"pan": "abc"` returns **400** with the field errors.
- **The open case:** `curl -s 'localhost:8080/api/cases?status=OPEN'` shows it. PAN and phone are masked (`******234F`, `******3210`).

---

## 3. Run the tests

| Command | What it runs | Needs Docker? |
|---|---|---|
| `mvn test` | All backend tests: rules, windows, API, masking, idempotency, case workflow, and Kafka using an in-memory broker (`@EmbeddedKafka`) | **No**. Uses H2 in PostgreSQL mode and an in-memory signal store |
| `mvn test -Pintegration` | Redis window test against a real Redis | Yes (`docker compose up -d redis`) |
| `cd ui && npm install && npm test` | React test: the queue renders only masked identifiers | No |

Result on the author's machine: `mvn test` passes 45 tests in 11 classes, and `npm test` passes 1 test.

| Requirement from the brief | Test class |
|---|---|
| Every rule: one positive and one negative case | `FraudRulesTest` |
| PASS/REVIEW boundary at exactly 30, REVIEW/REJECT at exactly 60 | `FraudEvaluatorTest` |
| 3rd PAN on one phone inside 30 days fires; older ones don't (fixed `Clock`) | `SignalWindowTest` |
| No full PAN or phone in `GET /api/cases` or `GET /api/cases/{id}` | `ApplicationApiTest` |
| Changing a threshold in config changes the decision | `ThresholdConfigTest` |
| Seeded fraud patterns produce cases | `GeneratorPipelineTest` |
| Same Kafka event twice gives 1 case, 1 rule-hit set, 1 decision event, no full PAN | `KafkaIngestionTest` |
| Malformed and conflicting events go to the DLT | `KafkaIngestionTest` |
| An exact repeat doesn't change Redis signals; a conflict is detected | `IdempotencyTest` |
| Valid transition writes audit; 409 on a final case; 400 on a short comment | `CaseWorkflowApiTest` |
| UI shows only masked identifiers | `ui/src/App.test.tsx` |

---

## 4. Generator and replay scripts

Both scripts need the stack and service from [Quick start](#1-quick-start) to be running.

### Generator: REST API, Task 1
```bash
scripts/generate.sh 500                  # [count] [baseUrl]
```
Creates 500 seeded synthetic applications, posts each one to `POST /api/applications`, and prints counts per decision and per rule. The seeded fraud patterns are:

| Pattern | Applications | Rule it should trigger |
|---|---|---|
| Phone ring: 8 different PANs on one phone | 8 | PHONE_SHARED, from the 3rd PAN on |
| Device ring: 8 different PANs on one device | 8 | DEVICE_SHARED, from the 3rd PAN on |
| Recycled PAN: one PAN with 5 different names | 5 | PAN_REUSED_DIFFERENT_IDENTITY, from the 2nd on |
| Inflated income | 30 | INCOME_INFLATED |
| Name on ID differs | 15 | KYC_MISMATCH |
| Thin bureau file, amount above 3,00,000 | 15 | THIN_FILE_HIGH_AMOUNT |
| Clean applicants | the rest | none |

### Replay: Kafka, Task 2
For clean numbers, start from an empty database and a fresh service:
```bash
docker compose down -v && docker compose up -d
set -a; source .env; set +a; mvn spring-boot:run      # in one terminal
scripts/replay.sh                                     # in another: [bootstrap] [baseUrl]
```
The replay publishes **1,000** new applications, **50** exact redeliveries, **10** conflicting redeliveries (same id, different data) and **10** malformed messages to `loan.applications.v1`. It then reads the service's own metrics and prints: applications evaluated, duplicates skipped, sent to DLT, cases created and p95 latency.

---

## 5. Underwriter review screen

```bash
cd ui
npm install
npm run dev            # open http://localhost:5173 (calls the API on :8080)
```

- **Header:** cases in view, high-risk count (score 60 or above) and cases awaiting an underwriter. All three count the cases in the current filter.
- **Queue (left):**
  - filter chips for OPEN, ESCALATED, APPROVED, REJECTED and ALL
  - highest score first, with red (60+) or amber (30–59) score badges
  - masked PAN and phone only
  - **System verdict** is the automatic PASS / REVIEW / REJECT. **Underwriter outcome** is the human case status.
  - An approved case whose verdict was REJECT gets an **OVERRIDDEN** badge.
- **Detail panel (right):** click any row to open it. It shows:
  - masked identifiers, device and amount
  - **why it was flagged**, one card per rule with its reason
  - the acting user, a comment box (at least 10 characters) and **APPROVE / REJECT / ESCALATE** buttons
  - a green banner on success, or a red banner with the API's own error message (for example *"Cannot REJECT a case that is APPROVED"*)
  - an amber warning before you override a REJECT verdict
  - the **audit trail** as a timeline
  - Final cases (APPROVED or REJECTED) show no action buttons.

---

## 6. Configuration

### Environment variables
These live in `.env`; copy them from `.env.example`.

| Variable | Default | Used for |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/doorstep` | PostgreSQL connection |
| `DB_USER` / `DB_PASSWORD` | `doorstep` / *(set it)* | PostgreSQL login, also used by docker compose |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka |

Never commit `.env`. It is in `.gitignore`.

### Fraud rules (`src/main/resources/application.yml`)
Every weight, window, limit and threshold is configuration. Change it there; no code change is needed.

| Rule | Points | Fires when | Settings |
|---|---|---|---|
| `PAN_REUSED_DIFFERENT_IDENTITY` | 60 | The same PAN appeared on another application in the last **30 days** with a different name or date of birth (names compared ignoring case and extra spaces) | `fraud.rules.pan-reused.*` |
| `PHONE_SHARED` | 35 | The phone is linked to **3 or more** distinct PANs in the last **30 days**, counting this one | `fraud.rules.phone-shared.*` |
| `DEVICE_SHARED` | 35 | The device is linked to **3 or more** distinct PANs in the last **24 hours**, counting this one | `fraud.rules.device-shared.*` |
| `KYC_MISMATCH` | 30 | KYC is not verified, or the name or DOB on the ID differs from the application | `fraud.rules.kyc-mismatch.*` |
| `THIN_FILE_HIGH_AMOUNT` | 15 | The bureau score is missing or history is under **6 months**, and the amount is above **3,00,000** | `fraud.rules.thin-file.*` |
| `INCOME_INFLATED` | 20 | Declared income is more than **3x** the bureau estimate | `fraud.rules.income-inflated.*` |

**Decision** = sum of the points of the rules that fired:

| Score | Decision | Opens a case? |
|---|---|---|
| below 30 | PASS | No |
| 30–59 | REVIEW | Yes |
| 60 or more | REJECT | Yes |

The thresholds are `fraud.thresholds.review` (30) and `fraud.thresholds.reject` (60). Example: start with `--fraud.thresholds.review=20` and an application that only trips INCOME_INFLATED (20 points) becomes REVIEW instead of PASS. `ThresholdConfigTest` proves this.

### Other switches
- `--doorstep.signals.store=memory` runs without Redis, using the in-memory signal store.
- `--doorstep.kafka.enabled=false` runs without Kafka. REST still works.

---

## 7. API reference

| Method and path | Purpose | Responses |
|---|---|---|
| `POST /api/applications` | Evaluate an application | 200 decision, score, ruleHits, caseId · 400 field errors · 409 same id with different data |
| `GET /api/cases?status=OPEN` | Case queue, highest score first (`status` is optional) | 200 |
| `GET /api/cases/{caseId}` | One case with its rule hits | 200 · 404 |
| `POST /api/cases/{caseId}/actions` | Underwriter action. Header `X-User: <name>`, body `{"action":"APPROVE"\|"REJECT"\|"ESCALATE","comment":"min 10 chars"}` | 200 updated case · 400 missing/short comment or missing X-User · 409 transition not allowed |
| `GET /api/cases/{caseId}/audit` | Audit history, oldest first | 200 |
| `GET /actuator/metrics/{name}` | Service metrics (used by the replay) | 200 |

**Kafka topics**

| Topic | Direction | Contents |
|---|---|---|
| `loan.applications.v1` | in | Application JSON (same shape as the REST body); message key = `applicationId` |
| `loan.applications.v1.DLT` | out | Malformed, invalid or conflicting messages, plus any that failed after 3 retries |
| `loan.decisions.v1` | out | `{applicationId, decision, score, ruleIds, maskedPan}`; never the full PAN |

---

## 8. How it works

```
 Partner channels ─► Kafka loan.applications.v1 ─► ApplicationEventListener ──┐
                              │ (bad / conflicting / failed 3x)                │
                              └──────► loan.applications.v1.DLT                │
 Mobile app / DSA ─► POST /api/applications ─► ApplicationController ─────────┤
                                                                              ▼
                                        ApplicationEvaluationService   (one shared path)
                                          1. already seen this applicationId?  ── yes ─► return stored result / 409
                                          2. SignalCollector ─► SignalStore ─► Redis   (what earlier applications show)
                                          3. FraudEvaluator  ─► 6 FraudRule beans      (score + reasons)
                                          4. save application, rule hits, case ─► PostgreSQL
                                          5. record signals for future applications ─► Redis
                                          6. after commit: decision event ─► Kafka loan.decisions.v1
 React review screen ─► /api/cases ... ─► CaseWorkflowService ─► fraud_case + case_audit (PostgreSQL)
```

### Key design decisions
- **The rules are pure functions.** The Redis lookups happen once, before the rules run, and produce a `CrossApplicationSignals` value. Each rule then decides from the application plus that value alone, so every rule is unit-tested without Redis or Spring.
- **Time windows in Redis use one sorted set per identifier, scored by `submittedAt`.**
  - Before counting, entries older than the window are trimmed (`ZREMRANGEBYSCORE`), so stale links never count.
  - "Now" comes from an injectable `Clock`, which is why the window test can pin time.
  - Keys and members hold **SHA-256 hashes**, never the raw PAN or phone.
  - Re-adding an existing member only refreshes it, so counts can't be double-incremented.
- **Masking:** full PAN and phone are stored only in PostgreSQL, the system of record. Every API response, rule reason, log line and Kafka decision event masks them as `******234F` / `******3210`.
- **Idempotency:** the application id is the database **primary key**, which is the unique constraint. A hash of the payload tells the two kinds of repeat apart:
  - **Exact repeat:** the stored result is returned and nothing new is written (no rule hits, case, Redis change or decision event).
  - **Same id, different data:** a conflict. REST returns 409; Kafka sends it to the DLT.
  - The database row is inserted *before* Redis is updated, so a racing duplicate fails on the primary key first.
- **Kafka offsets:** auto-commit is off and `ack-mode` is `record`. An offset is committed only after the message is processed successfully or written to the DLT, which gives at-least-once delivery. The idempotency above makes that safe.
  - Bad JSON, failed validation, a key that doesn't match `applicationId`, and conflicts go **straight to the DLT** with no retries.
  - Other errors (for example the database being down) are retried **3 times** with exponential backoff (200 ms, ×2), then dead-lettered.
- **Case state machine** (enforced on the server, in `CaseAction`):
  ```
  OPEN ──► APPROVED   (final)
  OPEN ──► REJECTED   (final)
  OPEN ──► ESCALATED ──► APPROVED / REJECTED
  ```
  Each successful action and its `case_audit` row are written in one transaction. The audit repository has no update or delete methods.

---

## 9. Measured numbers

Measured on a MacBook Air (Apple silicon) against `docker compose` PostgreSQL 16, Redis 7 and Kafka 3.8 (KRaft), on 14 Nov 2026.

### Generator through the REST API (Task 1): `scripts/generate.sh 500`
```
Posted 500 applications
Decisions: {PASS=469, REJECT=4, REVIEW=27}
Rule hits: {DEVICE_SHARED=6, INCOME_INFLATED=30, KYC_MISMATCH=15, PAN_REUSED_DIFFERENT_IDENTITY=4, PHONE_SHARED=6, THIN_FILE_HIGH_AMOUNT=15}
Cases created: 31
```
| Decision | Count | | Rule | Hits |
|---|---|---|---|---|
| PASS | 469 | | PHONE_SHARED | 6 |
| REVIEW | 27 | | DEVICE_SHARED | 6 |
| REJECT | 4 | | PAN_REUSED_DIFFERENT_IDENTITY | 4 |
| | | | KYC_MISMATCH | 15 |
| | | | INCOME_INFLATED | 30 |
| | | | THIN_FILE_HIGH_AMOUNT | 15 |

Cases = REVIEW + REJECT = 31. INCOME_INFLATED (20) and THIN_FILE_HIGH_AMOUNT (15) on their own stay below the REVIEW threshold of 30, so those applicants PASS with the reason recorded. This is intended under the default weights.

### Kafka replay (Task 2): `scripts/replay.sh`
```
Published 1070 messages; waiting for the service to drain...
Applications evaluated : 1000
Duplicates skipped     : 50
Sent to DLT            : 20
Cases created          : 32
p95 evaluation latency : 5.4 ms
```
- **Cases:** 31 of the 32 come from the replay (15 KYC + 6 phone ring + 6 device ring + 4 recycled PAN, equal to REVIEW + REJECT). The extra one came from a manual curl demo posted earlier in the same service run.
- **p95:** consume to decision, measured by the Micrometer timer `doorstep.evaluation.latency`. It is well under the 50 ms target.

---

## 10. Project layout

```
src/main/java/com/azentio/doorstep/
  api/          REST controllers, request/response DTOs, error handling
  rules/        the six FraudRule classes, FraudEvaluator, Decision
  signals/      SignalStore (Redis + in-memory), SignalCollector, hashing
  service/      evaluation (idempotent), case queries, case workflow
  persistence/  JPA entities, repositories, CaseAction state machine
  ingest/       Kafka listener, error handler / DLT, decision publisher
  generator/    synthetic data generator, GeneratorCli, ReplayCli
src/main/resources/
  application.yml           all configuration, including fraud rules
  db/migration/V1..V3       Flyway schema
src/test/                   unit and integration tests (see section 3)
ui/                         React + TypeScript (Vite) review screen
scripts/                    generate.sh, replay.sh
docker-compose.yml          PostgreSQL, Redis, Kafka (KRaft)
AI_USAGE.md                 how AI tools were used
```

---

## 11. Troubleshooting

| Symptom | Fix |
|---|---|
| `docker compose up` errors about `DB_PASSWORD` | Run `cp .env.example .env` first |
| Service fails with "Connection refused" (5432 / 6379 / 9092) | Docker Desktop isn't running, or the containers aren't up. Check `docker compose ps` |
| `password authentication failed` | You changed `DB_PASSWORD` after Postgres was created. Run `docker compose down -v`, then start again |
| UI shows no cases | Make sure the service is running on :8080 and post some applications (`scripts/generate.sh 500`) |
| Replay numbers are higher than expected | The counters cover the whole service run. Restart the service on a clean database (`docker compose down -v`) |
| Port already in use | Stop the other process on 5432, 6379, 9092, 8080 or 5173 |

---

## 12. What I would do next

- **Four-eyes rule for overrides:** approving a case the system scored REJECT should require ESCALATE first and then a senior underwriter. Today the brief's state machine allows OPEN → APPROVED for any case, and the UI only warns before an override and marks it OVERRIDDEN.
- **Real authentication:** Spring Security with UNDERWRITER / SENIOR_UNDERWRITER roles instead of the trusted `X-User` header.
- **Fuzzy name matching** (Jaro-Winkler) for KYC_MISMATCH and PAN reuse, to catch initials and swapped first and last names.
- **Atomic Redis read-and-record** with a Lua script, to close a small race between two simultaneous applications on the same phone.
- **An outbox table for decision events.** They're sent after commit today, so a crash in between could drop one.
- **A database trigger forbidding UPDATE/DELETE on `case_audit`**, as defence in depth.
- **Testcontainers integration tests** for PostgreSQL, Redis and Kafka under `-Pintegration`.
- **A GitHub Actions workflow** running `mvn test` and `npm test`.
