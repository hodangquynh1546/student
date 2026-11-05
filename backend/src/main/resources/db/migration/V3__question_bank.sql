CREATE TABLE questions (
    id UUID PRIMARY KEY,
    type VARCHAR(24) NOT NULL CHECK (type IN ('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','SHORT_ANSWER','ESSAY','MATCHING','ORDERING','FILL_IN_THE_BLANK')),
    subject VARCHAR(120) NOT NULL,
    grade_level VARCHAR(40),
    topic VARCHAR(160) NOT NULL,
    chapter VARCHAR(160),
    lesson VARCHAR(160),
    difficulty VARCHAR(10) NOT NULL CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    body TEXT NOT NULL,
    content JSONB NOT NULL DEFAULT '{}'::jsonb,
    answer_key JSONB NOT NULL,
    explanation TEXT,
    rubric TEXT,
    default_points NUMERIC(8,2) NOT NULL CHECK (default_points > 0 AND default_points <= 100),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT questions_body_nonempty CHECK (length(trim(body)) > 0)
);
CREATE INDEX questions_filter_idx ON questions(subject,grade_level,topic,difficulty) WHERE deleted_at IS NULL;
CREATE INDEX questions_search_idx ON questions USING GIN (to_tsvector('simple',body)) WHERE deleted_at IS NULL;
