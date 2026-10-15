CREATE TABLE auth_rate_windows (
    scope_hash VARCHAR(64) NOT NULL,
    window_start BIGINT NOT NULL,
    attempts INT NOT NULL,
    PRIMARY KEY (scope_hash, window_start)
);
CREATE INDEX ix_auth_rate_window ON auth_rate_windows(window_start);
