CREATE TABLE exams (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('EXAM','HOMEWORK','PRACTICE','PERIODIC','REVIEW','MOCK')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','CLOSED')),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    duration_minutes INTEGER CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 480),
    max_attempts INTEGER NOT NULL DEFAULT 1 CHECK (max_attempts BETWEEN 1 AND 20),
    show_result_immediately BOOLEAN NOT NULL DEFAULT false,
    show_answers_after_submit BOOLEAN NOT NULL DEFAULT false,
    shuffle_questions BOOLEAN NOT NULL DEFAULT false,
    shuffle_options BOOLEAN NOT NULL DEFAULT false,
    allow_draft BOOLEAN NOT NULL DEFAULT true,
    allow_late BOOLEAN NOT NULL DEFAULT false,
    total_points NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (total_points >= 0),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT exam_dates_valid CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at)
);
CREATE INDEX exams_status_window_idx ON exams(status,starts_at,ends_at) WHERE deleted_at IS NULL;

CREATE TABLE exam_questions (
    id UUID PRIMARY KEY,
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE RESTRICT,
    source_question_id UUID REFERENCES questions(id) ON DELETE SET NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    type VARCHAR(24) NOT NULL,
    body TEXT NOT NULL,
    content JSONB NOT NULL,
    answer_key JSONB NOT NULL,
    explanation TEXT,
    rubric TEXT,
    points NUMERIC(8,2) NOT NULL CHECK (points > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(exam_id,position)
);
CREATE INDEX exam_questions_exam_idx ON exam_questions(exam_id,position);

CREATE TABLE assignments (
    id UUID PRIMARY KEY,
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE RESTRICT,
    classroom_id UUID REFERENCES classrooms(id) ON DELETE RESTRICT,
    student_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    due_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT assignment_target CHECK ((classroom_id IS NULL) <> (student_id IS NULL))
);
CREATE UNIQUE INDEX assignment_class_unique ON assignments(exam_id,classroom_id) WHERE classroom_id IS NOT NULL AND revoked_at IS NULL;
CREATE UNIQUE INDEX assignment_student_unique ON assignments(exam_id,student_id) WHERE student_id IS NOT NULL AND revoked_at IS NULL;
CREATE INDEX assignment_student_idx ON assignments(student_id,due_at) WHERE revoked_at IS NULL;
CREATE INDEX assignment_class_idx ON assignments(classroom_id,due_at) WHERE revoked_at IS NULL;

CREATE TABLE exam_attempts (
    id UUID PRIMARY KEY,
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 1),
    status VARCHAR(24) NOT NULL CHECK (status IN ('IN_PROGRESS','SUBMITTED','EXPIRED','PENDING_GRADING','GRADED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deadline_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    submit_key UUID,
    objective_score NUMERIC(10,2),
    final_score NUMERIC(10,2),
    graded_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(exam_id,student_id,attempt_number),
    CONSTRAINT attempt_submit_time CHECK (submitted_at IS NULL OR submitted_at >= started_at)
);
CREATE UNIQUE INDEX one_open_attempt ON exam_attempts(exam_id,student_id) WHERE status='IN_PROGRESS';
CREATE INDEX attempts_student_idx ON exam_attempts(student_id,started_at DESC);
CREATE INDEX attempts_grading_idx ON exam_attempts(status,submitted_at) WHERE status IN ('PENDING_GRADING','SUBMITTED');

CREATE TABLE student_answers (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES exam_attempts(id) ON DELETE RESTRICT,
    exam_question_id UUID NOT NULL REFERENCES exam_questions(id) ON DELETE RESTRICT,
    answer JSONB NOT NULL,
    marked_for_review BOOLEAN NOT NULL DEFAULT false,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(attempt_id,exam_question_id)
);
CREATE INDEX student_answers_attempt_idx ON student_answers(attempt_id);

CREATE TABLE grading_results (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES exam_attempts(id) ON DELETE RESTRICT,
    exam_question_id UUID NOT NULL REFERENCES exam_questions(id) ON DELETE RESTRICT,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING_GRADING','AI_SUGGESTED','TEACHER_GRADED')),
    automatic_score NUMERIC(8,2),
    ai_suggested_score NUMERIC(8,2),
    teacher_score NUMERIC(8,2),
    teacher_comment TEXT,
    confirmed_by UUID REFERENCES users(id),
    confirmed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(attempt_id,exam_question_id)
);
CREATE TABLE grading_history (
    id UUID PRIMARY KEY,
    grading_result_id UUID NOT NULL REFERENCES grading_results(id) ON DELETE RESTRICT,
    changed_by UUID NOT NULL REFERENCES users(id),
    old_score NUMERIC(8,2),
    new_score NUMERIC(8,2),
    reason TEXT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX grading_history_result_idx ON grading_history(grading_result_id,changed_at DESC);
