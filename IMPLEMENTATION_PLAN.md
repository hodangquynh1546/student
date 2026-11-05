# EduTrack implementation plan

Status: active. A phase is complete only after its migration, backend rules, authorization, real frontend integration, failure states, and relevant tests pass. `PROJECT_STATUS.md` records evidence and remaining gaps.

| Phase | Deliverable | Verification gate |
| --- | --- | --- |
| 0 Foundation | Java 21 service, React app, PostgreSQL/Flyway, Compose, health checks, environment validation | Containers start together; migration and builds pass |
| 1 Identity | One teacher account, student/parent activation, login, rotating refresh sessions, verified parent links | Token reuse, role boundaries, and parent ownership tests |
| 2 Classroom | Student import/invitations, class lifecycle, enrollment history | Classroom API and role tests against PostgreSQL |
| 3 Content | Eight validated question types, exam builder, assignment targeting | Correct answer never appears in a student exam response |
| 4 Exam/grading | Server-timed attempts, autosave/resume, idempotent submit, objective scoring, teacher confirmed essay score | Concurrent submit, expiry, access, and grading tests |
| 5 AI | Optional Gemini adapter, structured essay suggestion, learning help | Real integration when configured; bounded failure fallback |
| 6 Analytics | Role-specific dashboards, explainable ranking, weekly/monthly reports and exports | Numeric reconciliation against source records; privacy tests |
| 7 Readiness | Responsive UI, accessibility, security, performance, backup/restore, deployment | Full Compose and end-to-end gates |

## Work sequence and ownership

1. Keep the database migrations and API contract in `docs/` as the shared reference. Backend and frontend changes must be reconciled here when behavior changes.
2. Implement vertical slices: schema → service → HTTP contract → UI → tests. Avoid labeling a migration or a screen alone as a finished module.
3. Prioritize student data isolation and answer secrecy before cosmetic work. A teacher may see the system's records; students see their own and assigned data; parents see linked children's published results only.
4. Preserve snapshots of exam questions at publication so edits to the question bank cannot alter an active or submitted exam.
5. Report test commands, results, and blockers in `PROJECT_STATUS.md` after each phase. Do not claim SMTP, Gemini, or deployment success without real credentials and checks.

## Immediate contract for phase 3–4

- `POST /api/v1/admin/exams` creates metadata/settings in `DRAFT`.
- `POST /api/v1/admin/exams/{id}/questions` adds a question snapshot with `{questionId, points?, position?}` while draft.
- `POST /api/v1/admin/exams/{id}/publish` checks question set, total points and dates atomically.
- `POST /api/v1/admin/exams/{id}/assignments` targets exactly one class or student.
- `GET /api/v1/student/exams` lists eligible assignments without answers.
- `POST /api/v1/student/exams/{id}/attempts` starts or resumes an allowed attempt using server time.
- `GET /api/v1/student/attempts/{id}` returns own attempt, public question content and saved answers.
- `PUT /api/v1/student/attempts/{id}/answers/{questionId}` autosaves `{answer, markedForReview}`.
- `POST /api/v1/student/attempts/{id}/submit` accepts `{submitKey}` and is idempotent.

The response shapes, authorization, and status codes are in `docs/api-specification.md`. Endpoints planned there are not necessarily implemented. The phase 3/4 core routes and teacher grading routes now exist; confirm actual behavior in tests before declaring either phase complete.

## Next implementation sequence

1. **Release gate and result detail:** require `published_at` for all student/parent result reads; add per-question detail with conditional answer release. Test unpublished and cross-student reads against PostgreSQL.
2. **Private essay evidence:** migrate file metadata, implement validated local storage adapter and private stream authorization, then add the student upload/list/delete and teacher preview. Verify malicious MIME, oversized content and cross-student downloads.
3. **AI evaluation history:** separate the provider from grading, validate the requested structured schema, record model/latency/fingerprint, enforce bounded retries, and keep the teacher grade authoritative. Test unconfigured/invalid/timeout cases with a test adapter; verify a real provider only when configured.
4. **Notifications:** add in-app rows and email outbox, mark-read APIs and frontend indicators. Make delivery retries bounded and audit failures.
5. **Ranking and analytics:** implement deterministic aggregates from published results with explainable formula and redacted student projection. Reconcile dashboard numbers against source records and test parent ownership.
6. **Reports and exports:** persist measured weekly/monthly snapshots, generate PDF/XLSX from the same snapshot, then add the role-specific screens. Test unauthorized export access.
7. **Acceptance:** run backend PostgreSQL integration tests, frontend build and user-flow tests, Compose health checks, migration from an empty database, and a deployment rehearsal. Record commands and results in `PROJECT_STATUS.md`.
