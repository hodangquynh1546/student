CREATE TABLE file_metadata (
    id UUID PRIMARY KEY,
    storage_key UUID NOT NULL UNIQUE,
    mime_type VARCHAR(32) NOT NULL CHECK (mime_type IN ('image/jpeg','image/png','image/webp','application/pdf')),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 10485760),
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE answer_attachments (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES exam_attempts(id) ON DELETE RESTRICT,
    exam_question_id UUID NOT NULL REFERENCES exam_questions(id) ON DELETE RESTRICT,
    file_id UUID NOT NULL UNIQUE REFERENCES file_metadata(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX answer_attachments_attempt_question_idx ON answer_attachments(attempt_id,exam_question_id);
