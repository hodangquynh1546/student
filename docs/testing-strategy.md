# Testing strategy

The test pyramid favors focused service tests plus PostgreSQL integration tests for constraints, transactions and authorization. Browser flows verify the contract after backend and frontend are connected. A test is meaningful only when it exercises a business rule rather than repeating implementation details.

## Backend gates

- JUnit/Mockito: type-specific question validation, time-window decisions, objective scoring (exact and configured partial credit), answer normalization, ranking formula and AI schema validation.
- PostgreSQL integration (Testcontainers): Flyway migrations, one-teacher constraint, unique active enrollment/link, refresh rotation/reuse revocation, deadline/attempt limits, submit idempotency and concurrent submit, grade history, parent revocation and student isolation.
- HTTP/security: anonymous 401, wrong role 403, student A unable to read/save student B's attempt, unrelated parent unable to read a child, teacher access, no answer key in student JSON before release. Assert sanitized failure body.
- External adapter tests: Mailpit/dev delivery, AI request timeout/retry and malformed JSON fallback using a test double; run a real Gemini smoke check only when credentials are configured and record it separately.
- Import/report/ranking/notification tests: CSV quoting and row errors, Excel dates and formula rejection, partial import, live ranking ties and privacy, completed-period snapshot generation, PDF/XLSX content and private exports, notification recipient resolution and own-read authorization. Use a real PostgreSQL schema for cross-table and permission checks.

## Frontend gates

- Vitest/Testing Library: forms, validation, loading/error/empty states, role navigation, exam countdown from server timestamps, autosave indicator and submit confirmation.
- Playwright against the real backend/PostgreSQL test stack: admin login → class/student/parent invitation → question/exam/assignment → student resume/submit → teacher grade/publish → parent report. Use seeded test accounts, not hardcoded production data.
- Browser accessibility and responsive checks: keyboard exam navigation, focus after dialogs, two small mobile breakpoints and no hidden essential action.

## Acceptance evidence

Record the exact commands, environment, counts and failures in `PROJECT_STATUS.md`. A Java compile or a migration pass does not imply the LMS is complete. Required final gates: backend and frontend builds with no TypeScript errors, migrations from an empty PostgreSQL database, Compose startup/health, key Playwright flow, security isolation tests, and manual smoke checks of configured SMTP/AI. Distinguish provider tests using a local test double from calls to a configured live provider.
