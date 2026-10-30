-- Spring runs schema scripts on every startup. Guard each MySQL ALTER so both
-- existing databases and fresh installations can use this additive upgrade.
SET @monitoring_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'services' AND column_name = 'monitor_key'),
    'SELECT 1',
    'ALTER TABLE services ADD COLUMN monitor_key VARCHAR(100) NULL, ADD UNIQUE KEY uq_services_monitor_key (monitor_key)'
);
PREPARE monitoring_stmt FROM @monitoring_ddl;
EXECUTE monitoring_stmt;
DEALLOCATE PREPARE monitoring_stmt;

SET @monitoring_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'status_logs' AND column_name = 'checked_at'),
    'SELECT 1',
    'ALTER TABLE status_logs
        MODIFY COLUMN stats ENUM(''healthy'', ''sick'', ''down'', ''error''),
        ADD COLUMN checked_at DATETIME(6) NULL,
        ADD COLUMN duration_ms BIGINT NULL,
        ADD COLUMN exit_code INT NULL,
        ADD COLUMN stdout MEDIUMTEXT NULL,
        ADD COLUMN stderr MEDIUMTEXT NULL,
        ADD COLUMN check_error TEXT NULL,
        ADD COLUMN output_truncated BOOLEAN NOT NULL DEFAULT FALSE,
        ADD INDEX idx_status_logs_service_checked (service_id, checked_at)'
);
PREPARE monitoring_stmt FROM @monitoring_ddl;
EXECUTE monitoring_stmt;
DEALLOCATE PREPARE monitoring_stmt;

-- The existing message board maps to this table, but 0000.sql did not create it.
CREATE TABLE IF NOT EXISTS status_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    message TEXT NOT NULL
);
