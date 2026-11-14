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

## Where the AI was wrong and how it was caught
**Caught by running the code on my machine:**
- **The first `mvn test` failed to compile.** In `ApplicationApiTest` the AI had named a helper method `post(ApplicationRequest)`, which hid the statically imported `MockMvcRequestBuilders.post(String)`. I ran the build, pasted the error back, and the helper was renamed to `submit(...)`. The fix went on the Task 1 branch, where the bug came from. After that, `mvn test` passed: 45 tests in 11 classes, all offline. `npm test` passed the first time.
- **UI review on the live stack.** On a case with a final status (APPROVED or REJECTED), the screen still showed the action buttons, even though the server always refuses with 409. I spotted this while clicking through and asked for it to be fixed. It was also unclear that "Decision" (the system's verdict) and "Status" (the underwriter's outcome) are different things, so an approved REJECT looked like a bug. The columns were renamed, and overrides now show a warning and an OVERRIDDEN badge.

**Caught by the AI itself while writing (I reviewed the fixes):**
- A `private final var` test field. `var` is not legal Java for fields; it was changed to the explicit type.
- `@Validated` + `@NotBlank` on the `X-User` header would have produced a 500 (`ConstraintViolationException`), not a 400. It was replaced with an explicit blank check that maps to 400.
- An empty placeholder class (`Repositories.java`) and an unused test field: dead code, removed.
- Hibernate `ddl-auto: validate` could have failed differently on H2 and PostgreSQL for `timestamptz`/`Instant` columns. It was set to `none` so Flyway is the single owner of the schema.

## What I verified myself
- Every rule's reason string masks the PAN and phone.
- The phone-window test passes for the right reason: the old entries really are trimmed by score, not just absent.
- Redelivery doesn't touch Redis: repeated members are set elements, and the duplicate path returns before `SignalCollector.record`.
