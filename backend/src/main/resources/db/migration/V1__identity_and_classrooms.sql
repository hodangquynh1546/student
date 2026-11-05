CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30),
    date_of_birth DATE,
    avatar_url VARCHAR(2048),
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN_TEACHER','STUDENT','PARENT')),
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING_ACTIVATION','ACTIVE','LOCKED','DISABLED')),
    password_hash VARCHAR(255),
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT active_user_has_password CHECK (status <> 'ACTIVE' OR password_hash IS NOT NULL)
);
CREATE UNIQUE INDEX users_email_unique ON users (lower(email));
CREATE UNIQUE INDEX only_one_admin_teacher ON users ((role)) WHERE role = 'ADMIN_TEACHER';
CREATE INDEX users_role_status_idx ON users (role, status);

CREATE TABLE action_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('ACTIVATE','RESET_PASSWORD','VERIFY_EMAIL','PARENT_LINK')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    context_id UUID,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX action_tokens_user_purpose_idx ON action_tokens (user_id, purpose, expires_at);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_sessions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    user_agent VARCHAR(500)
);
CREATE INDEX refresh_sessions_user_idx ON refresh_sessions (user_id, expires_at DESC);
CREATE INDEX refresh_sessions_family_idx ON refresh_sessions (family_id);

CREATE TABLE parent_link_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_email VARCHAR(254) NOT NULL,
    requested_by UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REVOKED','EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ
);
CREATE INDEX parent_link_requests_email_idx ON parent_link_requests (lower(parent_email), status);
CREATE TABLE parent_student_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    verified_request_id UUID NOT NULL REFERENCES parent_link_requests(id),
    CONSTRAINT parent_not_student CHECK (parent_id <> student_id)
);
CREATE UNIQUE INDEX active_parent_student_link ON parent_student_links(parent_id, student_id) WHERE revoked_at IS NULL;
CREATE INDEX parent_student_links_student_idx ON parent_student_links(student_id) WHERE revoked_at IS NULL;

CREATE TABLE classrooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    subject VARCHAR(120) NOT NULL,
    grade_level VARCHAR(40),
    academic_year VARCHAR(20) NOT NULL,
    starts_on DATE,
    ends_on DATE,
    image_url VARCHAR(2048),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','COMPLETED','ARCHIVED')),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT classroom_dates_valid CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX classrooms_status_year_idx ON classrooms(status, academic_year) WHERE deleted_at IS NULL;

CREATE TABLE student_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id UUID NOT NULL REFERENCES classrooms(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    enrolled_by UUID NOT NULL REFERENCES users(id),
    CONSTRAINT enrollment_dates_valid CHECK (left_at IS NULL OR left_at >= joined_at)
);
CREATE UNIQUE INDEX one_active_enrollment ON student_enrollments(classroom_id, student_id) WHERE left_at IS NULL;
CREATE INDEX enrollments_student_idx ON student_enrollments(student_id, left_at);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    subject_type VARCHAR(60) NOT NULL,
    subject_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_subject_idx ON audit_events(subject_type, subject_id, created_at DESC);
CREATE INDEX audit_events_actor_idx ON audit_events(actor_id, created_at DESC);
