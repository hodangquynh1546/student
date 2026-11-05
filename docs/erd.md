# Entity relationships

This diagram reflects migrations V1–V9. Email outbox, class-wide reports and ranking snapshots are still planned.

```mermaid
erDiagram
  users ||--o{ refresh_sessions : owns
  users ||--o{ action_tokens : receives
  users ||--o{ parent_link_requests : student
  parent_link_requests ||--o| parent_student_links : verifies
  users ||--o{ parent_student_links : parent
  users ||--o{ parent_student_links : student
  classrooms ||--o{ student_enrollments : contains
  users ||--o{ student_enrollments : joins
  users ||--o{ questions : creates
  users ||--o{ exams : creates
  exams ||--|{ exam_questions : snapshots
  questions o|--o{ exam_questions : source
  exams ||--o{ assignments : assigned
  classrooms o|--o{ assignments : target
  users o|--o{ assignments : target_student
  exams ||--o{ exam_attempts : taken
  users ||--o{ exam_attempts : student
  exam_attempts ||--o{ student_answers : stores
  exam_questions ||--o{ student_answers : answered
  exam_attempts ||--o{ grading_results : scored
  exam_questions ||--o{ grading_results : scored_question
  grading_results ||--o{ grading_history : audited
  grading_results ||--o{ ai_evaluations : suggested
  exam_attempts ||--o{ answer_attachments : evidence
  exam_questions ||--o{ answer_attachments : evidence_question
  file_metadata ||--o| answer_attachments : stored_file
  users ||--o{ file_metadata : uploader
  users ||--o{ report_snapshots : learner
  users ||--o{ notifications : recipient
```

The relationship from exam to questions is logically one or more only after publication. A draft can have zero questions. Database constraints enforce keys and uniqueness; publication service must enforce the remaining business condition.
