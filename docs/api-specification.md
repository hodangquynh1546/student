# HTTP API contract

Base path `/api/v1`. JSON over HTTPS. Bearer JWT protects APIs except the specified account recovery and activation calls. Refresh credentials use an HttpOnly cookie scoped to `/api/v1/auth`; frontend requests to refresh/logout must send credentials. On the hosted frontend, route `/api` to the backend through the same origin. IDs are UUIDs and timestamps are ISO 8601 UTC. The table distinguishes **implemented** from **planned** paths; a planned route is not a working feature.

## Shared rules

- `400` malformed input, `401` unauthenticated, `403` authenticated but forbidden, `404` unavailable resource, `409` conflict/stale version, `429` throttled, `500` unexpected error. Do not leak internal exception text or hidden resource existence to unauthorized users.
- List responses should use `{items,total,page,size}` with a maximum page size of 100. Existing classroom/student lists still return arrays; migrate deliberately rather than breaking an integrated client.
- Admin read/write routes require `ADMIN_TEACHER`. Student and parent routes also verify ownership or a verified active relationship in the service, not just the route role.
- Teacher exam responses may include `answerKey`; student exam/attempt responses must omit it until the explicit answer-release policy permits review.

## Identity and classroom: current paths

| Method and path | Role | Request / response | Status |
| --- | --- | --- | --- |
| `POST /auth/login` | Public | `{email,password}` → `{accessToken,expiresIn,user}` and refresh cookie | Implemented |
| `POST /auth/refresh` | Cookie | Empty body → rotated access token and cookie | Implemented |
| `POST /auth/logout` | Cookie | Revokes current refresh session | Implemented |
| `GET /auth/me` | Any authenticated | Current account profile | Implemented |
| `PUT /auth/me` | Any authenticated | Update own name, phone and birth date; email, role and avatar are preserved | Implemented |
| `POST /auth/forgot-password` | Public | `{email}`; generic accepted response | Implemented |
| `POST /auth/reset-password` | Public | `{token,password}` | Implemented |
| `POST /auth/activate` | Public | `{token,password}` | Implemented |
| `POST /auth/change-password` | Any authenticated | `{currentPassword,newPassword}` | Implemented |
| `POST /admin/students` | Teacher | `{email,fullName,phone?,dateOfBirth?,parentEmail?}` → pending student | Implemented |
| `POST /admin/students/import` | Teacher | Multipart `file` (UTF-8 CSV, XLSX or XLS), max 2 MB/500 rows; `{created,failed,errors:[{row,message}]}` | Implemented; row-level partial success |
| `GET /admin/students` | Teacher | Student list | Implemented |
| `POST /admin/students/{id}/resend-activation` | Teacher | Empty body | Implemented |
| `PATCH /admin/students/{id}/status?status=LOCKED\|ACTIVE` | Teacher | Updated student | Implemented |
| `POST /admin/classrooms` | Teacher | `{code,name,description?,subject,gradeLevel?,academicYear,startsOn?,endsOn?}` | Implemented |
| `GET /admin/classrooms`, `GET /admin/classrooms/{id}` | Teacher | Class list/detail | Implemented |
| `PUT /admin/classrooms/{id}` | Teacher | Full class metadata | Implemented |
| `PATCH /admin/classrooms/{id}/status?value=...` | Teacher | `DRAFT`, `ACTIVE`, `COMPLETED`, `ARCHIVED` | Implemented |
| `DELETE /admin/classrooms/{id}` | Teacher | Soft delete | Implemented |
| `POST /admin/classrooms/{classId}/students/{studentId}` | Teacher | Enroll student | Implemented |
| `DELETE /admin/classrooms/{classId}/students/{studentId}` | Teacher | Close active enrollment | Implemented |
| `GET /admin/classrooms/{id}/students` | Teacher | Active students | Implemented |
| `GET /student/classrooms` | Student | Own active classes | Implemented |
| `POST /admin/students/{studentId}/parent-links` | Teacher | `{parentEmail}` → invitation metadata | Implemented |
| `GET /admin/students/{studentId}/parent-links` | Teacher | Link requests | Implemented |
| `DELETE /admin/parent-links/{requestId}` | Teacher | Revoke link | Implemented |
| `POST /parent/links/accept` | Parent | `{token}`; token and email verified | Implemented |
| `GET /parent/children` | Parent | Verified active children | Implemented |

## Question bank: current paths

Teacher only: `POST /admin/questions`, `GET /admin/questions`, `GET /admin/questions/{id}`, `PUT /admin/questions/{id}?version=N`, `DELETE /admin/questions/{id}?version=N`. The list has `subject`, `topic`, `type`, `difficulty`, `page`, `size`. `PUT` and `DELETE` reject stale versions with `409`.

Create/update body: `{type,subject,gradeLevel?,topic,chapter?,lesson?,difficulty,body,content,answerKey,explanation?,rubric?,defaultPoints}`. `type` is one of `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `SHORT_ANSWER`, `ESSAY`, `MATCHING`, `ORDERING`, `FILL_IN_THE_BLANK`. `content` and `answerKey` schemas are validated per type. Teacher response contains `version` and answer key. Rich HTML and media upload are not yet complete.

## Exam builder and assignment: current and extension contract

The table's core routes are implemented. `GET /admin/exams` currently returns an array, without the planned pagination/filter arguments. Draft editing, ordering, preview and inline question creation remain planned.

| Method and path | Request | Response / invariant |
| --- | --- | --- |
| `POST /admin/exams` | `{title,description?,kind,startsAt?,endsAt?,durationMinutes?,maxAttempts,showResultImmediately,showAnswersAfterSubmit,shuffleQuestions,shuffleOptions,allowDraft,allowLate}` | `201 ExamView` in `DRAFT`. `kind`: `EXAM`, `HOMEWORK`, `PRACTICE`, `PERIODIC`, `REVIEW`, `MOCK`. |
| `GET /admin/exams` | `page,size,status?,kind?` | Paged exam summaries. |
| `GET /admin/exams/{id}` | — | Teacher exam detail and ordered question snapshots. |
| `POST /admin/exams/{id}/questions` | `{questionId,points?,position?}` | `201` snapshot from question bank; draft only. Points default to bank default. Recalculate total atomically. |
| `POST /admin/exams/{id}/publish` | — | Published exam. Reject zero questions, invalid dates/settings, and stale edits. |
| `POST /admin/exams/{id}/assignments` | `{classroomId?,studentId?,dueAt?}` | `201`; exactly one target. Student target must have STUDENT role. |

Future builder extensions: `PUT /admin/exams/{id}` for draft metadata, `PUT /admin/exams/{id}/questions/{examQuestionId}` for draft question points/content, `PUT /admin/exams/{id}/questions/order` with ordered IDs, `DELETE /admin/exams/{id}/questions/{examQuestionId}`, `POST /admin/exams/{id}/close`, and preview. Inline question creation should call the bank API first or a dedicated atomic command. A published question snapshot is immutable.

## Student exam engine: current contract

All six routes below are implemented. `GET /student/exams` accepts `page,size`; the optional status filter remains planned. The current result DTO is only `{id,examId,status,score,totalPoints,publishedAt}`; per-question results and answers remain planned.

| Method and path | Request | Response / invariant |
| --- | --- | --- |
| `GET /student/exams` | Optional `page,size,status` | Only eligible assigned exams and own attempt state; no answers. |
| `POST /student/exams/{id}/attempts` | Empty body | `201` new or `200` resumable `AttemptView`; server chooses attempt number and deadline. Reject not assigned, closed, out of window or max attempts. |
| `GET /student/attempts/{id}` | — | Own attempt, ordered public questions, own saved answers, server time and deadline. No answer key. |
| `PUT /student/attempts/{id}/answers/{questionId}` | `{answer,markedForReview}` | Saved answer and server `savedAt`; only own `IN_PROGRESS` attempt before deadline. |
| `POST /student/attempts/{id}/submit` | `{submitKey:UUID}` | Idempotent submission summary. Same key and attempt returns same result; a conflicting submit cannot double grade. |
| `GET /student/attempts/{id}/result` | — | Only according to exam result policy and publication. Correct answers appear only when configured and allowed. |

`AttemptView` contains `{id,examId,status,startedAt,deadlineAt,serverTime,questions:[{id,type,body,content,points}],answers:[{questionId,answer,markedForReview,savedAt}]}`. `content` must be scrubbed of any correct-answer markers. The student identity always comes from the access token; no caller-supplied student ID is trusted. Submit uses an attempt row lock and unique constraints.

## Grading: current paths

All paths are relative to `/api/v1`. Teacher only. These are the implemented route names; future clients should use them rather than the earlier `/admin/attempts` proposal.

| Method and path | Contract | Status |
| --- | --- | --- |
| `GET /admin/grading/queue` | Up to 100 pending attempt summaries `{attemptId,examTitle,studentName,submittedAt}` | Implemented; pagination/filter planned |
| `GET /admin/grading/attempts/{id}` | Attempt detail, including answer key, automatic score, AI score and teacher score per question | Implemented |
| `PUT /admin/grading/attempts/{id}/questions/{questionId}` | `{score,comment?,reason}`; `0 <= score <= question.points`, two decimal places; append immutable grade history | Implemented |
| `POST /admin/grading/attempts/{id}/publish` | Reject pending essay questions; mark result published | Implemented |
| `POST /admin/grading/attempts/{id}/questions/{questionId}/ai-suggestion` | Legacy `{score,feedback}`; essay only, teacher grade never overwritten | Implemented; structured evaluation/history route below is preferred |

## Results, media and later slices

The tables below mix implemented routes with future targets; each section states current scope. Every route resolves identity from the JWT; `studentId` in a path selects a record but never grants access. Unauthorized private records return `404` where possible. Some current lists return arrays or Spring pages rather than the proposed `{items,total,page,size}` envelope.

### Results and parent access (parent core implemented; student extensions planned)

| Method and path | Role | Response / rule |
| --- | --- | --- |
| `GET /student/attempts/{id}/result` | Student | Own result according to exam visibility and publication policy; implemented. |
| `GET /student/results?page=&size=` | Student | Planned own published-result list. |
| `GET /student/attempts/{id}/result-detail` | Student | Planned per-question result detail. |
| `GET /parent/children/{studentId}/results?page=&size=` | Parent | Implemented `{items,total,page,size}` of published summaries for an actively linked child. |
| `GET /parent/children/{studentId}/results/{attemptId}` | Parent | Implemented `{result,questions}` with published score and teacher comment; no answer key. |

Current parent `ResultSummary = {attemptId,examId,examTitle,kind,score,totalPoints,submittedAt,publishedAt}`. Parent `ResultDetail` adds `questions:[{questionId,type,body,maxPoints,score,teacherComment}]`. The parent DTO deliberately omits answer keys and student answers. Richer student history and per-question review remain planned.

### Private attachments (implemented core)

| Method and path | Role | Request / response / rule |
| --- | --- | --- |
| `POST /student/attempts/{attemptId}/questions/{questionId}/attachments` | Student | Multipart `file`, own in-progress ESSAY only; `201 Attachment`. Signature/MIME validation permits JPG, PNG, WEBP, PDF; max five files per answer and configured size capped at 10 MB. |
| `GET /student/attempts/{attemptId}/questions/{questionId}/attachments` | Student | Own attachment list; no storage path. |
| `GET /admin/grading/attempts/{attemptId}/questions/{questionId}/attachments` | Teacher | Evidence list for grading. |
| `GET /parent/children/{studentId}/results/{attemptId}/questions/{questionId}/attachments` | Parent | Evidence list only with active verified link and published result. |
| `GET /files/{fileId}/content` | Teacher, owner student or linked parent | Private download, authorized per file; `nosniff` and `no-store` headers. |

`Attachment = {id,questionId,mimeType,byteSize,createdAt}`. No delete endpoint is implemented. Never accept a storage path, URL, or owner ID from the caller. Database rows bind file → question/attempt → student; storage currently uses local private files behind an abstraction.

### Notifications (implemented core)

| Method and path | Role | Contract |
| --- | --- | --- |
| `GET /notifications?page=&size=` | Authenticated | Own Spring `Page<Notice>`; size 1–100. |
| `GET /notifications/unread-count` | Authenticated | `{count}` for current account. |
| `POST /notifications/{id}/read` | Authenticated | Mark own notification read; idempotent. |
| `POST /notifications/read-all` | Authenticated | Mark caller's notifications read; `{updated}`. |
| `POST /admin/notifications` | Teacher | `{recipientId,type,title,body}` → `201 Notice`. |

`Notice = {id,type,title,body,createdAt,readAt}`. Assignment and result-publication notifications resolve recipients from persisted assignments and verified active family links inside the same transaction. The teacher-create route accepts an explicit recipient ID. Event-based email delivery/outbox and reminder scheduling remain planned.

### Ranking, dashboards and reports (implemented core)

The three dashboard routes are implemented in `AnalyticsController`. Student dashboard returns assigned/completed/pending exam counts, completion rate, published score statistics, a 30-point trend and topic performance. Parent dashboard uses published facts for a verified linked child. Admin dashboard returns student/class/exam counts, pending grading and upcoming exam counts, assignment completion and average published percentage. Other fields described in the product requirements are planned.

| Method and path | Role | Contract |
| --- | --- | --- |
| `GET /admin/classrooms/{classroomId}/ranking?period=ALL\|WEEK\|MONTH\|SEMESTER&examId=` | Teacher | Named rows and all score inputs for active class members. |
| `GET /student/classrooms/{classroomId}/ranking?period=&examId=` | Student | Own rank and metrics only; active enrollment required. |
| `GET /parent/children/{studentId}/classrooms/{classroomId}/ranking?period=&examId=` | Parent | Linked child's rank and metrics only; active enrollment required. |
| `GET /admin/analytics/dashboard` | Teacher | System counts, completion, published score aggregates, grading queue and upcoming exams. |
| `GET /student/analytics/dashboard` | Student | Own assignments, published score trend, progress and topic strengths/weaknesses. |
| `GET /parent/children/{studentId}/analytics/dashboard` | Parent | Published facts for linked child only. |
| `GET /admin/students/{studentId}/reports` | Teacher | Latest 100 student snapshots. |
| `POST /admin/students/{studentId}/reports?period=WEEK\|MONTH&start=YYYY-MM-DD` | Teacher | Generate or update a completed calendar period snapshot. |
| `GET /student/reports` | Student | Own snapshots. |
| `GET /parent/children/{studentId}/reports` | Parent | Linked child's snapshots only. |
| `GET /admin/students/{studentId}/reports/{reportId}/export?format=PDF\|XLSX` | Teacher | Private PDF or Excel attachment. |
| `GET /student/reports/{reportId}/export?format=PDF\|XLSX` | Student | Own report export. |
| `GET /parent/children/{studentId}/reports/{reportId}/export?format=PDF\|XLSX` | Parent | Verified linked child's report export. |

Ranking uses the latest published attempt per eligible assigned exam and normalizes scores to 0–100. Composite = `70% average + 20% completion + 10% progress`. Progress is `clamp(50 + (later-half average − earlier-half average)/2,0,100)`; no published result gives 0, one gives 50. Sort by composite, average, completion, progress and stable student UUID. Exact metric ties share a competition rank. The teacher response includes the formula and named rows; student and parent responses include only the requested child's metrics. Periods are rolling 7, 30 and 180 days, or all time; `examId` selects one class-assigned exam.

Reports use UTC calendar weeks starting Monday and calendar months. Only completed periods can be generated. Assignment/completion facts derive from due dates; score averages and topics derive from published results. Weekly and monthly generation is scheduled for the prior period at 01:20 UTC Monday and 02:20 UTC on the first day of each month. Snapshots are updated on repeat generation. Class-wide reports, AI narrative and reminder email are not implemented.

### AI services

| Method and path | Role | Contract |
| --- | --- | --- |
| `GET /ai/status` | Authenticated | Planned; missing key/model currently causes AI routes to return `503`. |
| `POST /admin/grading/attempts/{id}/questions/{questionId}/ai-evaluations` | Teacher | Generate a structured essay evaluation; return `200 AiEvaluationView` or an existing evaluation for the same fingerprint. Teacher grade remains authoritative. |
| `GET /admin/grading/attempts/{id}/questions/{questionId}/ai-evaluations` | Teacher | Immutable history, newest first. |
| `POST /ai/student/ask` | Student | `{question,attemptId?,questionId?}` → `{answer,model}`. Optional context requires own published result and an ended/closed exam; answer key is never loaded. |
| `POST /ai/admin/questions/generate` | Teacher | `{subject,topic,gradeLevel,difficulty,type,count}` → `{drafts,model}`; 1–5 drafts, never silently saved to bank. |

`AiEvaluationView = {id,suggestedScore,maxScore,evaluation,strengths,weaknesses,mistakes,improvementSuggestions,confidence,gradingReason,model,processingMs,createdAt}`. Structured essay evaluations and their history are implemented, including authorized image/PDF attachments. Provider output is validated and cannot modify teacher grades. Actual provider behavior remains unverified without credentials. The learning and draft-generation routes have in-process per-user hourly limits; draft JSON receives structural validation, while full question-bank validation happens only when the teacher saves a draft.
