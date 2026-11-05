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

