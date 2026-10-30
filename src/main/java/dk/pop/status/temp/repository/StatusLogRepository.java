package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.utils.UnitOfWork;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Repository
public class StatusLogRepository implements UnitOfWork {
    private final DataSource dataSource;

    private final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

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
