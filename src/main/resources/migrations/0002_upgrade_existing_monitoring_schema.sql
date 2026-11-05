-- The configurable retry-threshold feature needs one additional service field.
-- Log column differences are handled by StatusLogRepository in Java.

SET @popnet_has_retry_threshold = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'services' AND column_name = 'retry_threshold'
);
SET @popnet_sql = IF(@popnet_has_retry_threshold = 0,
    'ALTER TABLE services ADD COLUMN retry_threshold INT NOT NULL DEFAULT 3', 'SELECT 1');
PREPARE popnet_statement FROM @popnet_sql;
EXECUTE popnet_statement;
DEALLOCATE PREPARE popnet_statement;

-- 0000.sql creates this column for new databases, but CREATE TABLE IF NOT
-- EXISTS does not add it to an already-existing status_logs table.
SET @popnet_has_delay_ms = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'status_logs'
      AND column_name = 'delay_ms'
);
SET @popnet_sql = IF(@popnet_has_delay_ms = 0,
    'ALTER TABLE status_logs ADD COLUMN delay_ms INT', 'SELECT 1');
PREPARE popnet_statement FROM @popnet_sql;
EXECUTE popnet_statement;
DEALLOCATE PREPARE popnet_statement;
