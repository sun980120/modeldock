CREATE TABLE app_users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(60) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(12) NOT NULL,
    status VARCHAR(12) NOT NULL,
    daily_limit BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CHECK (daily_limit >= 0),
    CHECK (role IN ('USER', 'ADMIN')),
    CHECK (status IN ('PENDING','APPROVED','REJECTED','SUSPENDED'))
);
CREATE TABLE runtime_settings (
    id INT PRIMARY KEY,
    fusion_json TEXT NOT NULL,
    service_daily_limit BIGINT NOT NULL,
    share_service_budget BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO runtime_settings VALUES (1, '{"models":[],"judge":"","outputTokens":1024,"timeoutSeconds":120,"concurrency":4}', 100000000, FALSE);
CREATE TABLE conversations (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES app_users(id)
);
CREATE INDEX ix_conversations_owner_updated ON conversations(owner_id, deleted, updated_at, id);
CREATE TABLE daily_budgets (
    scope_id VARCHAR(36) NOT NULL,
    budget_day DATE NOT NULL,
    used_tokens BIGINT NOT NULL DEFAULT 0,
    reserved_tokens BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (scope_id, budget_day),
    CHECK (used_tokens >= 0 AND reserved_tokens >= 0)
);
CREATE TABLE generations (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    budget_day DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    reserved_tokens BIGINT NOT NULL,
    reported_tokens BIGINT,
    charged_tokens BIGINT,
    usage_quality VARCHAR(16) NOT NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(48),
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (owner_id) REFERENCES app_users(id)
);
CREATE INDEX ix_generation_active ON generations(status, expires_at);
CREATE INDEX ix_generation_owner ON generations(owner_id, created_at);
CREATE INDEX ix_generation_owner_status ON generations(owner_id, status);
CREATE INDEX ix_generation_conversation ON generations(conversation_id, created_at);
CREATE TABLE messages (
    seq BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    generation_id VARCHAR(36) NOT NULL,
    role VARCHAR(12) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE (generation_id, role),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (generation_id) REFERENCES generations(id)
);
CREATE INDEX ix_messages_conversation ON messages(conversation_id, seq);
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL PRIMARY KEY,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION(SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION(EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION(PRINCIPAL_NAME);
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BLOB NOT NULL,
    PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
