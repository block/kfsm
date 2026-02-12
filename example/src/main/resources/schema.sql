-- Document uploads table
CREATE TABLE IF NOT EXISTS document_uploads (
    id VARCHAR(36) PRIMARY KEY,
    state VARCHAR(50) NOT NULL,
    state_data JSON,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_at TIMESTAMP(6) NOT NULL,
    file_storage_id VARCHAR(255),
    scan_report JSON,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

-- Outbox table for reliable effect processing (matches lib-jooq schema)
CREATE TABLE IF NOT EXISTS outbox_messages (
    id VARCHAR(36) PRIMARY KEY,
    value_id VARCHAR(255) NOT NULL,
    effect_type VARCHAR(255) NOT NULL,
    effect_payload TEXT NOT NULL,
    dedup_key VARCHAR(255),
    depends_on_effect_id VARCHAR(36),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    processed_at TIMESTAMP(6),
    
    INDEX idx_outbox_pending (status, created_at),
    INDEX idx_outbox_value_id (value_id, status, created_at),
    INDEX idx_outbox_dedup (dedup_key),
    INDEX idx_outbox_depends (depends_on_effect_id)
);

-- Pending requests table for awaitable state machine (matches lib-jooq schema)
CREATE TABLE IF NOT EXISTS pending_requests (
    id VARCHAR(36) PRIMARY KEY,
    value_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    result_payload TEXT,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6),

    INDEX idx_pending_requests_value_status (value_id, status),
    INDEX idx_pending_requests_status_created (status, created_at)
);
