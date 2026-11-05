package dk.pop.status.temp.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ResponseTimeRepository {
    private final JdbcClient jdbc;

    public record Hour(LocalDateTime start, long total, long checks) {
    }

    public ResponseTimeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void add(long serviceId, LocalDateTime checkedAt, int delayMs) {
        jdbc.sql("INSERT INTO response_times (service_id, checked_at, delay_ms) VALUES (?, ?, ?)")
                .params(serviceId, checkedAt, delayMs)
                .update();
    }

    public void deleteBefore(LocalDateTime time) {
        jdbc.sql("DELETE FROM response_times WHERE checked_at < ?")
                .param(time)
                .update();
    }

    public List<Hour> hours(long serviceId, LocalDateTime from, LocalDateTime to) {
        return jdbc.sql("""
                        SELECT FLOOR(TIMESTAMPDIFF(SECOND, ?, checked_at) / 3600) AS hour_index,
                               SUM(delay_ms) AS total, COUNT(*) AS checks
                        FROM response_times
                        WHERE service_id = ? AND checked_at >= ? AND checked_at < ?
                        GROUP BY hour_index
                        """)
                .params(from, serviceId, from, to)
                .query((results, row) -> new Hour(from.plusHours(results.getLong("hour_index")),
                        results.getLong("total"), results.getLong("checks")))
                .list();
    }
}
