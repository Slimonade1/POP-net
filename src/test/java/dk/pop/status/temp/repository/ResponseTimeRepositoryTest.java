package dk.pop.status.temp.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseTimeRepositoryTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 5, 8, 0);

    private ResponseTimeRepository repository;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("migrations/0000.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                CREATE TABLE response_times (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    service_id BIGINT NOT NULL,
                    checked_at DATETIME NOT NULL,
                    delay_ms INT NOT NULL
                )
                """).update();
        jdbc.sql("INSERT INTO services (id, name) VALUES (1, 'Website'), (2, 'Wiki')").update();
        repository = new ResponseTimeRepository(jdbc);
    }

    @Test
    void responseTimesAreAddedUpPerHour() {
        repository.add(1, START.plusMinutes(10), 100);
        repository.add(1, START.plusMinutes(50), 200);
        repository.add(1, START.plusHours(2).plusMinutes(5), 40);
        repository.add(2, START.plusMinutes(10), 999);

        var hours = repository.hours(1, START, START.plusHours(3));

        assertThat(hours).containsExactlyInAnyOrder(
                new ResponseTimeRepository.Hour(START, 300, 2),
                new ResponseTimeRepository.Hour(START.plusHours(2), 40, 1));
    }

    @Test
    void oldResponseTimesAreDeleted() {
        repository.add(1, START, 100);
        repository.add(1, START.plusDays(1), 100);

        repository.deleteBefore(START.plusHours(1));

        assertThat(jdbc.sql("SELECT COUNT(*) FROM response_times").query(Integer.class).single()).isEqualTo(1);
    }
}
