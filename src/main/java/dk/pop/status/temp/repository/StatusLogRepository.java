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

@Repository
public class StatusLogRepository implements UnitOfWork {
    private final DataSource dataSource;

    private final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

    public Map<Long, StatusLog> findLatestByService() {
        var sql = """
                SELECT l.*, s.name AS service_name
                FROM services s
                JOIN status_logs l ON l.id = (
                    SELECT recent.id FROM status_logs recent
                    WHERE recent.service_id = s.id
                    ORDER BY recent.date DESC, recent.time DESC, recent.id DESC
                    LIMIT 1
                )
                """;

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql);
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
                SELECT l.*, s.name AS service_name
                FROM status_logs l
                JOIN services s ON s.id = l.service_id
                WHERE l.service_id = ?
                ORDER BY l.date DESC, l.time DESC, l.id DESC
                LIMIT ?
                """;

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
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

    private static StatusLog readLog(ResultSet results) throws SQLException {
        return new StatusLog(
                results.getLong("id"),
                results.getString("service_name"),
                results.getString("status"),
                results.getObject("time", LocalTime.class),
                results.getObject("date", LocalDate.class),
                results.getString("message"),
                results.getObject("delay_ms", Integer.class));
    }

    public void create(StatusLog statusLog) {
        var findServiceId = "SELECT id FROM services WHERE name = ?";
        
        var stmt = """
        INSERT INTO status_logs
            (service_id, status, time, date, message, delay_ms)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (var findStatement = getConnection().prepareStatement(findServiceId)) {
            findStatement.setString(1, statusLog.service_name());

            try (var resultSet = findStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new RuntimeException("Service not found: " + statusLog.service_name());
                }

                long serviceId = resultSet.getLong("id");

                try (var insertStatement = getConnection().prepareStatement(stmt)) {
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
