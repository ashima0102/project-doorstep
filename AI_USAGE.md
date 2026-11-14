# AI usage

## Tools
- **Claude (Opus, agentic mode in the Claude app)** wrote the code, tests and docs, and drove git. I reviewed every file and decided the design.

## How I worked with it
1. I gave it the full assignment PDF and asked for a plan first: task breakdown, branch names and commit messages. Nothing was written until I approved it.
2. I worked task by task, as the brief asks: Task 1 on `feature/task1-fraud-check-api`, then Task 2 and Task 3 on their own branches. Each step got a small Conventional Commit.
3. Its sandbox had no network, so it could not run Maven or npm. I ran `mvn test` and `npm test` on my machine and fed the failures back to it.

## Key prompts
- "Read the PDF properly, make tasks, finish one task properly before the next, commit with proper branch names and messages, don't push. First tell me the plan."
- Design constraints I asked for during the work:
  - rules must be pure functions so they can be tested without Redis
  - Redis windows must be sorted sets scored by `submittedAt`, trimmed on read, with an injectable `Clock`
  - idempotency must rest on the primary key plus a payload hash, not an in-memory set

## Where the AI was wrong and how I caught it
- **It wrote `private final var` for a test field.** That is not legal Java (`var` works only for local variables). I caught it in review and changed it to the explicit type.
- **It first put `@Validated` + `@NotBlank` on the `X-User` header.** That triggers AOP method validation, which throws `ConstraintViolationException` (a 500 with our handler), not a 400. It also risks changing how `@Valid @RequestBody` errors are reported. I replaced it with an explicit blank check that maps to 400, plus a handler for a missing header.
- **It left an empty placeholder class (`Repositories.java`) and an unused field in a test.** Both were dead code, and I removed them.
- **Hibernate `ddl-auto: validate` against `timestamptz` / `Instant` mappings** risked startup failures that differ between H2 and PostgreSQL. I set it to `none`, so Flyway is the single owner of the schema.
- _Add anything `mvn test` / `npm test` surfaced on my machine and how it was fixed._

## What I verified myself
- Every rule's reason string masks the PAN and phone.
- The phone-window test passes for the right reason: the old entries really are trimmed by score, not just absent.
- Redelivery doesn't touch Redis: repeated members are set elements, and the duplicate path returns before `SignalCollector.record`.
