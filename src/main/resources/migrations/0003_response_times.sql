CREATE TABLE IF NOT EXISTS response_times (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT NOT NULL,
    checked_at DATETIME NOT NULL,
    delay_ms INT NOT NULL,
    FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
);

SET @popnet_has_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'response_times'
      AND index_name = 'response_times_service_time'
);
SET @popnet_sql = IF(@popnet_has_index = 0,
    'CREATE INDEX response_times_service_time ON response_times (service_id, checked_at)', 'SELECT 1');
PREPARE popnet_statement FROM @popnet_sql;
EXECUTE popnet_statement;
DEALLOCATE PREPARE popnet_statement;
