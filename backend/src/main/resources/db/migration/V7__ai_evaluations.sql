CREATE TABLE ai_evaluations (
    id UUID PRIMARY KEY,
    grading_result_id UUID NOT NULL REFERENCES grading_results(id) ON DELETE RESTRICT,
    fingerprint CHAR(64) NOT NULL UNIQUE,
    model VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING','SUCCEEDED','FAILED')),
    response JSONB,
    processing_ms BIGINT CHECK (processing_ms IS NULL OR processing_ms >= 0),
    error_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT ai_response_status CHECK (
        (status='SUCCEEDED' AND response IS NOT NULL AND completed_at IS NOT NULL)
        OR (status='PROCESSING' AND response IS NULL AND completed_at IS NULL)
        OR (status='FAILED' AND response IS NULL AND completed_at IS NOT NULL)
    )
);
CREATE INDEX ai_evaluations_result_idx ON ai_evaluations(grading_result_id,created_at DESC);
