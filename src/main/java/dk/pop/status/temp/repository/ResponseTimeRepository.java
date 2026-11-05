package dk.pop.status.temp.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Repository
public class ResponseTimeRepository {
    private final JdbcClient jdbc;

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

    public Map<Integer, Integer> averages(long serviceId, LocalDateTime from, LocalDateTime to, long stepSeconds) {
        Map<Integer, Integer> averages = new HashMap<>();
        jdbc.sql("""
                        SELECT FLOOR(TIMESTAMPDIFF(SECOND, ?, checked_at) / ?) AS step, ROUND(AVG(delay_ms)) AS average
                        FROM response_times
                        WHERE service_id = ? AND checked_at >= ? AND checked_at < ?
                        GROUP BY step
                        """)
                .params(from, stepSeconds, serviceId, from, to)
                .query(results -> {
                    averages.put(results.getInt("step"), results.getInt("average"));
                });
        return averages;
    }
}
