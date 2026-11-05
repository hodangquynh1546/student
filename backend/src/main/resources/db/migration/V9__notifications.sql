CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL CHECK (type IN ('GENERAL','ACCOUNT','CLASSROOM','ASSIGNMENT','RESULT','REPORT')),
    title VARCHAR(180) NOT NULL CHECK (length(btrim(title)) > 0),
    body VARCHAR(2000) NOT NULL CHECK (length(btrim(body)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    CONSTRAINT notification_read_after_creation CHECK (read_at IS NULL OR read_at >= created_at)
);

CREATE INDEX notifications_recipient_created_idx ON notifications(recipient_id, created_at DESC, id DESC);
CREATE INDEX notifications_recipient_unread_idx ON notifications(recipient_id, created_at DESC) WHERE read_at IS NULL;
