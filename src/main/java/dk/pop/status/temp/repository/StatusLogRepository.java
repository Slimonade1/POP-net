package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
public class StatusLogRepository {
    private final JdbcTemplate jdbc;

    public StatusLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void create(StatusLog log) {
        // A stable configuration ID keeps history together when a display name changes.
        jdbc.update("""
                INSERT INTO services (monitor_key, name) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE name = ?
                """, log.serviceKey(), log.serviceName(), log.serviceName());
        Long serviceId = jdbc.queryForObject(
                "SELECT id FROM services WHERE monitor_key = ?", Long.class, log.serviceKey());
        LocalDateTime checkedAt = LocalDateTime.ofInstant(log.checkedAt(), ZoneOffset.UTC);
        String summary = log.errorMessage() == null ? log.status() : log.errorMessage();
        jdbc.update("""
                INSERT INTO status_logs
                    (service_id, stats, time, date, message, checked_at, duration_ms,
                     exit_code, stdout, stderr, check_error, output_truncated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, serviceId, log.status(), checkedAt.toLocalTime(), checkedAt.toLocalDate(),
                summary.substring(0, Math.min(summary.length(), 255)), checkedAt,
                log.durationMs(), log.exitCode(), log.stdout(), log.stderr(),
                log.errorMessage(), log.outputTruncated());
    }
}
