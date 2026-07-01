-- V1 baseline (Phase 0). Proves Flyway connects and migrates on startup.
-- Domain schema (users, families, medication_schedules, ...) is added in later phases.
CREATE TABLE IF NOT EXISTS app_info (
    key   VARCHAR(50) PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);

INSERT INTO app_info (key, value)
VALUES ('phase', '0')
ON CONFLICT (key) DO NOTHING;
