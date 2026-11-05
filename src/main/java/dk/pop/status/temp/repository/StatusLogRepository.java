package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.utils.UnitOfWork;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;

@Repository
public class StatusLogRepository implements UnitOfWork {
    private final DataSource dataSource;

    private final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();
    private volatile LogColumns logColumns;

    private record LogColumns(String status, String duration, String durationRead) {
    }

    private LogColumns columns(Connection connection) throws SQLException {
        if (logColumns != null) {
            return logColumns;
        }
        try (var statement = connection.prepareStatement("SELECT * FROM status_logs WHERE 1 = 0");
             var result = statement.executeQuery()) {
            var names = new HashSet<String>();
            var metadata = result.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                names.add(metadata.getColumnName(i).toLowerCase(Locale.ROOT));
            }
            String status = names.contains("stats") ? "stats" : "status";
            String duration = names.contains("duration_ms") ? "duration_ms" : "delay_ms";
            // A database upgraded by the previous migration may contain both.
            String read = names.contains("duration_ms") && names.contains("delay_ms")
                    ? "COALESCE(l.duration_ms, l.delay_ms)" : "l." + duration;
            logColumns = new LogColumns(status, duration, read);
            return logColumns;
        }
    }

    public Map<Long, StatusLog> findLatestByService() {
        var sql = """
                SELECT l.*, l.%s AS diagnostic_status, %s AS diagnostic_duration, s.name AS service_name
                FROM services s
                JOIN status_logs l ON l.id = (
                    SELECT recent.id FROM status_logs recent
                    WHERE recent.service_id = s.id
                    ORDER BY recent.date DESC, recent.time DESC, recent.id DESC
                    LIMIT 1
                )
                """;

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql.formatted(columns(connection).status(), columns(connection).durationRead()));
             var results = statement.executeQuery()) {
            Map<Long, StatusLog> logs = new HashMap<>();
            while (results.next()) {
                logs.put(results.getLong("service_id"), readLog(results));
            }
            return logs;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read latest status logs", e);
        }
    }

    public List<StatusLog> findRecentByServiceId(long serviceId, int limit) {
        var sql = """
                SELECT l.*, l.%s AS diagnostic_status, %s AS diagnostic_duration, s.name AS service_name
                FROM status_logs l
                JOIN services s ON s.id = l.service_id
                WHERE l.service_id = ?
                ORDER BY l.date DESC, l.time DESC, l.id DESC
                LIMIT ?
                """;

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql.formatted(columns(connection).status(), columns(connection).durationRead()))) {
            statement.setLong(1, serviceId);
            statement.setInt(2, limit);
            try (var results = statement.executeQuery()) {
                List<StatusLog> logs = new ArrayList<>();
                while (results.next()) {
                    logs.add(readLog(results));
                }
                return logs;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read service status history", e);
        }
    }

    public List<StatusLog> findLastBefore(LocalDate date) {
        var sql = """
                SELECT l.*, l.%s AS diagnostic_status, %s AS diagnostic_duration, s.name AS service_name
                FROM services s
                JOIN status_logs l ON l.id = (
                    SELECT earlier.id FROM status_logs earlier
                    WHERE earlier.service_id = s.id AND earlier.date < ?
                    ORDER BY earlier.date DESC, earlier.time DESC, earlier.id DESC
                    LIMIT 1
                )
                """;

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql.formatted(columns(connection).status(), columns(connection).durationRead()))) {
            statement.setDate(1, java.sql.Date.valueOf(date));
            try (var results = statement.executeQuery()) {
                List<StatusLog> logs = new ArrayList<>();
                while (results.next()) {
                    logs.add(readLog(results));
                }
                return logs;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read status history", e);
        }
    }

    public List<StatusLog> findSince(LocalDate from) {
        var sql = """
                SELECT l.*, l.%s AS diagnostic_status, %s AS diagnostic_duration, s.name AS service_name
                FROM status_logs l
                JOIN services s ON s.id = l.service_id
                WHERE l.date >= ?
                ORDER BY l.date, l.time, l.id
                """;

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql.formatted(columns(connection).status(), columns(connection).durationRead()))) {
            statement.setDate(1, java.sql.Date.valueOf(from));
            try (var results = statement.executeQuery()) {
                List<StatusLog> logs = new ArrayList<>();
                while (results.next()) {
                    logs.add(readLog(results));
                }
                return logs;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read status history", e);
        }
    }

    private static StatusLog readLog(ResultSet results) throws SQLException {
        return new StatusLog(
                results.getLong("id"),
                results.getString("service_name"),
                results.getString("diagnostic_status"),
                results.getObject("time", LocalTime.class),
                results.getObject("date", LocalDate.class),
                results.getString("message"),
                results.getObject("diagnostic_duration", Integer.class));
    }

    public void create(StatusLog statusLog) {
        var findServiceId = "SELECT id FROM services WHERE name = ?";
        
        var stmt = """
        INSERT INTO status_logs
            (service_id, %s, time, date, message, %s)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (var findStatement = getConnection().prepareStatement(findServiceId)) {
            findStatement.setString(1, statusLog.service_name());

            try (var resultSet = findStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new RuntimeException("Service not found: " + statusLog.service_name());
                }

                long serviceId = resultSet.getLong("id");

                var columns = columns(getConnection());
                try (var insertStatement = getConnection().prepareStatement(stmt.formatted(columns.status(), columns.duration()))) {
                    insertStatement.setLong(1, serviceId);
                    insertStatement.setString(2, statusLog.status());
                    insertStatement.setTime(3, java.sql.Time.valueOf(statusLog.time()));
                    insertStatement.setDate(4, java.sql.Date.valueOf(statusLog.date()));
                    insertStatement.setString(5, statusLog.message());
                    insertStatement.setInt(6, statusLog.delay_ms());

                    insertStatement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create status log", e);
        }
    }

    private Connection getConnection() {
        Connection conn = connectionHolder.get();
        if (conn == null) {
            throw new RuntimeException("Connection not opened");
        }
        return conn;
    }

    public StatusLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UnitOfWork begin() throws UnitOfWorkException {
        try {
            Connection conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            connectionHolder.set(conn);
            return this;
        } catch (SQLException e) {
            throw new UnitOfWorkException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commit() throws UnitOfWorkException {
        try {
            Connection conn = connectionHolder.get();
            if (conn != null) conn.commit();
        } catch (SQLException e) {
            throw new UnitOfWorkException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollback() throws UnitOfWorkException {
        try {
            Connection conn = connectionHolder.get();
            if (conn != null) conn.rollback();
        } catch (SQLException e) {
            throw new UnitOfWorkException("Failed to roll back transaction", e);
        }
    }

    @Override
    public void close() throws UnitOfWorkException {
        try {
            Connection conn = connectionHolder.get();
            if (conn != null) {
                conn.close();
                connectionHolder.remove();
            }
        } catch (SQLException e) {
            throw new UnitOfWorkException("Failed to close connection", e);
        }
    }
}
