# Database design

PostgreSQL 16+ is the source of truth. Flyway owns all schema changes; Hibernate uses `ddl-auto=validate`. UUID primary keys and foreign keys prevent accidental cross-record joins. Timestamps and versions support auditability and safe edits. The current schema is in migrations V1–V9; future groups below remain planned.

| Group | Current tables | Planned or extension |
| --- | --- | --- |
| Identity | `users`, `action_tokens`, `refresh_sessions`, `parent_link_requests`, `parent_student_links` | separate email delivery log and session management fields |
| Classroom | `classrooms`, `student_enrollments` | class activity history / import batches |
| Question | `questions` | optional normalized tags and media references |
| Assessment | `exams`, `exam_questions`, `assignments`, `exam_attempts`, `student_answers`, `answer_attachments` | attempt events |
| Grading | `grading_results`, `grading_history`, `ai_evaluations` | provider operation auditing |
| Analytics | `report_snapshots` | class report snapshots, optional ranking snapshots |
| Notification | `notifications` | durable email outbox and delivery log |
| System | `audit_events` | standalone file metadata for non-answer media |

The user has exactly one role in V1 (`users.role`) because registration and product rules do not permit mixed roles. There is no redundant roles join table. If dual roles become a real requirement, migrate explicitly with a new join table and authorization tests. The partial unique index `only_one_admin_teacher` is a database guarantee, including disabled accounts.

`parent_link_requests` records the teacher's invitation to a specific student and email. `parent_student_links` records accepted, revocable relationships; an email match alone grants no access. `student_enrollments` records each enrollment interval, allowing a student to leave and rejoin without deleting learning history.

`questions.content` and `answer_key` are JSONB because each of eight question types has distinct structured data. They are validated by type before storage. Relational metadata (`subject`, `topic`, `difficulty`, points and owner) remains queryable and indexed. `exam_questions` stores a full copy so a later bank edit does not change an existing exam. The answer key must be loaded only on grading/teacher paths.

`assignments` targets exactly one class or student by a check constraint. An eligible student must be assigned directly or have an active class enrollment when starting an attempt; the decision about access after leaving the class must be explicit and tested. `exam_attempts` has a unique `(exam_id, student_id, attempt_number)` and a partial unique index for one in-progress attempt. Lock the attempt row during submit and grade once. `student_answers` is unique per attempt/question.

Use a new Flyway migration for all future changes. Never change an applied migration. In production, backups must include database and private file storage with coordinated restore points.

## Applied extensions and remaining migrations

V6 stores private essay attachment metadata bound to an answer and attempt. V7 stores structured AI evaluations with a unique request fingerprint, model, status, response and duration. V8 stores weekly/monthly student report snapshots with a unique student/period/start key, measured counts and a compact JSONB topic breakdown. V9 stores recipient-scoped in-app notifications with read timestamps. All are applied by Flyway before application startup; tests using a real PostgreSQL instance should verify the migrations before deployment.

CSV/XLSX/XLS student import reuses the existing `users`, action token and parent-link structures; there is no import batch table yet. Ranking is calculated live from class assignments, current enrollment and published attempts; there is no ranking snapshot table. A report can be regenerated for a closed period, updating the existing snapshot under its unique key.

Keep each group in a new ordered Flyway migration and add foreign keys, ownership indexes and check constraints. These definitions are contracts for implementation, not existing tables.

| Table / change | Required columns and constraints | Main indexes |
| --- | --- | --- |
| `email_outbox` | `id`, recipient address, template and structured variables, status, attempt count, next attempt time, last error code, created/sent timestamps; idempotency/event key unique | `(status,next_attempt_at)` |
| `import_batches` (optional) | file hash, actor, counts, per-row outcome and timestamps; never persist raw import content | `(actor_id,created_at DESC)` |
| `class_report_snapshots` | class FK, period, measured aggregates, generation and release timestamps | `(classroom_id,period_start DESC)` |
| `ranking_snapshots` (optional) | class FK, period window, formula version, generated time, opaque rows with student FK and normalized metrics; use only if live ranking becomes too expensive | `(classroom_id,period_start DESC)` |

Current essay files are stored via `answer_attachments`, not a general media table. A future general media table should use opaque storage keys and an explicit owner/authorization chain. Deleted blobs need bounded cleanup, and no browser-supplied filesystem path may be stored.

For parent queries, join `parent_student_links` by the current parent ID, requested child ID and active verified state before joining published attempts. This prevents an email match or an old revoked link from granting access. Reports and ranking must use `exam_attempts.published_at IS NOT NULL`; aggregate only finalized grades. Store each ranking formula version and period boundaries with snapshots so historic ranks remain explainable.
