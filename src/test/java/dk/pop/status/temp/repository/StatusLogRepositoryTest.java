package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatusLogRepositoryTest {
    private StatusLogRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("migrations/0000.sql")).execute(dataSource);
        repository = new StatusLogRepository(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        // Deliberately identical names: reads must associate diagnostics by service ID.
        jdbc.update("INSERT INTO services (id, name) VALUES (1, 'Shared name'), (2, 'Shared name'), (3, 'Unchecked')");
    }

    @Test
    void latestLogsUseDateTimeAndIdAndKeepServicesSeparate() {
        insertLog(1, 1, "down", "2026-11-04", "14:00:00", "Old failure");
        insertLog(2, 1, "down", "2026-11-05", "09:00:00", "HTTP Fault: 503");
        insertLog(3, 1, "healthy", "2026-11-05", "09:00:00", "HTTP 200 OK");
        insertLog(4, 1, "down", "2026-11-05", "08:00:00", "Earlier time, higher ID");
        insertLog(5, 1, "down", "2026-11-03", "15:00:00", "Earlier date, higher ID");
        insertLog(6, 2, "down", "2026-11-05", "10:00:00", "Packet Loss Timeout Drop");

        var latest = repository.findLatestByService();

        assertThat(latest).containsOnlyKeys(1L, 2L);
        assertThat(latest.get(1L)).isEqualTo(new StatusLog(3L, "Shared name", "healthy",
                LocalTime.of(9, 0), LocalDate.of(2026, 11, 5), "HTTP 200 OK", 123));
        assertThat(latest.get(2L).message()).isEqualTo("Packet Loss Timeout Drop");
    }

    @Test
    void historyIsLimitedAndOrderedAndOnlyIncludesRequestedService() {
        insertLog(1, 1, "down", "2026-11-04", "09:00:00", "Old failure");
        insertLog(2, 1, "down", "2026-11-05", "09:00:00", "Failure");
        insertLog(3, 1, "healthy", "2026-11-05", "09:00:00", "Recovery");
        insertLog(4, 1, "down", "2026-11-05", "08:00:00", "Earlier time");
        insertLog(5, 2, "down", "2026-11-05", "10:00:00", "Other service");

        assertThat(repository.findRecentByServiceId(1, 2))
                .extracting(StatusLog::id).containsExactly(3L, 2L);
        assertThat(repository.findRecentByServiceId(1, 50))
                .extracting(StatusLog::id).containsExactly(3L, 2L, 4L, 1L);
    }

    @Test
    void servicesWithoutLogsHaveNoInventedStatus() {
        assertThat(repository.findLatestByService()).isEmpty();
        assertThat(repository.findRecentByServiceId(3, 50)).isEmpty();
    }

    @Test
    void readsNullableDiagnosticFields() {
        jdbc.update("INSERT INTO status_logs (id, service_id, status) VALUES (1, 1, 'down')");

        var expected = new StatusLog(1L, "Shared name", "down", null, null, null, null);
        assertThat(repository.findLatestByService().get(1L)).isEqualTo(expected);
        assertThat(repository.findRecentByServiceId(1, 50)).containsExactly(expected);
    }

    private void insertLog(long id, long serviceId, String status, String date, String time, String message) {
        jdbc.update("""
                INSERT INTO status_logs (id, service_id, status, date, time, message, delay_ms)
                VALUES (?, ?, ?, ?, ?, ?, 123)
                """, id, serviceId, status, LocalDate.parse(date), LocalTime.parse(time), message);
    }
}
