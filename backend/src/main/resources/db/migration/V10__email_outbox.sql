CREATE TABLE email_logs (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL UNIQUE REFERENCES notifications(id) ON DELETE CASCADE,
    source_kind VARCHAR(16) NOT NULL CHECK (source_kind IN ('ASSIGNMENT', 'RESULT')),
    source_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'CANCELLED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(120),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT email_log_sent_consistency CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

CREATE INDEX email_logs_due_idx ON email_logs(next_attempt_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');
