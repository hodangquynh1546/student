CREATE TABLE report_snapshots (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    period_type VARCHAR(8) NOT NULL CHECK (period_type IN ('WEEK','MONTH')),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    assigned_count INTEGER NOT NULL CHECK (assigned_count >= 0),
    completed_count INTEGER NOT NULL CHECK (completed_count >= 0),
    published_result_count INTEGER NOT NULL CHECK (published_result_count >= 0),
    average_percent NUMERIC(6,2),
    progress_points NUMERIC(7,2),
    topics JSONB NOT NULL DEFAULT '[]'::jsonb,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT report_period_valid CHECK (period_end > period_start),
    CONSTRAINT report_completed_valid CHECK (completed_count <= assigned_count),
    CONSTRAINT report_average_valid CHECK (average_percent IS NULL OR average_percent BETWEEN 0 AND 100),
    UNIQUE (student_id, period_type, period_start)
);
CREATE INDEX report_snapshots_student_period_idx ON report_snapshots(student_id,period_start DESC);
