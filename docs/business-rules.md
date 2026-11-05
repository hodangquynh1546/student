# Business rules

## Accounts and family links

Only an environment-seeded ADMIN_TEACHER exists; a database unique index rejects a second teacher. There is no public teacher signup. A teacher creates students in `PENDING_ACTIVATION`, using an expiring one-time email token. Activation sets a password and verifies the mailbox. Parent links require a teacher invitation to a specific student, a parent account with the invited email, and acceptance with a one-time token. A parent can link many children; a child can have multiple verified parents. Revocation ends access immediately. Knowing a student's email never grants access.

Teacher import accepts UTF-8 CSV or Excel XLSX/XLS up to 2 MB and 500 student rows. `email` and `fullName` columns are required; `phone`, `dateOfBirth` (`YYYY-MM-DD`) and `parentEmail` are optional. Duplicate or invalid rows are reported individually; valid rows create pending accounts and reuse the same invitation path as single creation. The response counts created and failed rows, so teachers must review partial imports.

## Class and assignment

One student may join multiple classes. Enrollment intervals persist after leaving. A class is soft deleted, preserving submissions and historical membership. Class assignments should be evaluated from active enrollment when an exam starts. If a student leaves after starting, the attempt remains part of the historical record but policy for further attempts must be tested explicitly. A direct assignment is scoped to exactly one student. Duplicate live class/student assignments are rejected.

## Questions and exams

The eight question types have different answer structures; validate them before persistence. Teacher editing a bank question increments its version. An exam question snapshots content and answer key. A published exam cannot be silently changed by editing the bank. Publication requires at least one valid question and a valid availability window. Settings define attempts, time, late submission, result visibility and answer release. The server clock determines all deadlines.

## Attempt and grading

Each attempt has a monotonically assigned number per student/exam. At most one attempt is open at a time. Autosave is allowed only for an owned open attempt and a question in that exam. A submitted/expired attempt is immutable. The same submit key returns the same outcome; a new key cannot create an extra score for an already submitted attempt. Objective grading is deterministic from stored snapshot answer keys. Short text can normalize case and whitespace when configured. Partial credit applies only where the question configuration explicitly permits it. Essay scores stay pending until a teacher confirms. AI suggestions never become official automatically. Every teacher score change creates an append-only history entry with reason.

## Results, ranking and reports

Students see their own results under exam visibility settings. Parents see only their linked children's published results. Teacher may inspect all. Current ranking includes active class members, class-assigned exams and their latest published attempt per exam. It normalizes scores to 0–100 and weights average score 70%, completion 20% and progress 10%. Rank order uses composite, average, completion, progress and stable student UUID; exact metric ties share the displayed rank. Student and parent routes return only the requested learner's row, while teacher sees the named class. Weekly and monthly snapshots are generated only for completed UTC calendar periods and derive counts, score averages, progress and topic results from stored assignments and published attempts. A repeat generation refreshes the same snapshot; an AI narrative may restate facts but cannot invent figures.

In-app notifications for a new assignment or published result are created from persisted business records. Result notifications reach the student and only parents whose link is currently verified and active. Notification text contains no score. Each account may list or mark only its own notices; email notifications and scheduled due-date reminders are still future work.

AI tutoring can use a student's own question and answer as context only after the result is published and the exam has closed or ended. It does not load the correct answer key. Teacher AI generation returns drafts for review; it does not save or publish them. Missing AI configuration returns an explicit unavailable response. AI suggestions never change the confirmed teacher grade.
