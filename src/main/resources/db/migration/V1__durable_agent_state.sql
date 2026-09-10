CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    tenant VARCHAR(80) NOT NULL,
    owner_id VARCHAR(200) NOT NULL,
    transcript TEXT NOT NULL DEFAULT '[]',
    active_run UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX sessions_owner_idx ON sessions(tenant,owner_id);
CREATE TABLE runs (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    tenant VARCHAR(80) NOT NULL,
    requester VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    state_json TEXT NOT NULL,
    error_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(session_id,idempotency_key)
);
CREATE INDEX runs_recovery_idx ON runs(status,deadline);
CREATE TABLE approvals (
    run_id UUID PRIMARY KEY REFERENCES runs(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    call_id VARCHAR(200) NOT NULL,
    tool VARCHAR(80) NOT NULL,
    arguments TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_by VARCHAR(200),
    decision_reason VARCHAR(500)
);
CREATE TABLE service_orders (
    tenant VARCHAR(80) NOT NULL,
    order_id VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_paise BIGINT NOT NULL CHECK(total_paise > 0),
    PRIMARY KEY(tenant,order_id)
);
CREATE TABLE service_credits (
    id UUID PRIMARY KEY,
    tenant VARCHAR(80) NOT NULL,
    order_id VARCHAR(40) NOT NULL,
    amount_paise BIGINT NOT NULL CHECK(amount_paise BETWEEN 1 AND 50000),
    run_id UUID NOT NULL UNIQUE,
    approved_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(tenant,order_id),
    FOREIGN KEY(tenant,order_id) REFERENCES service_orders(tenant,order_id)
);
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    tenant VARCHAR(80) NOT NULL,
    run_id UUID,
    event_type VARCHAR(60) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX audit_time_idx ON audit_events(created_at);
