package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusLog;

import dk.pop.status.temp.utils.UnitOfWork;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.annotation.RequestScope;

import javax.sql.DataSource;
import java.sql.Connection;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import static java.sql.DriverManager.getConnection;

@Repository
@RequestScope
public class StatusLogRepository implements UnitOfWork {
    private final DataSource dataSource;
    private Connection connection;

    public void create(StatusLog statusLog) {
        var findServiceId = "SELECT id FROM services WHERE name = ?";
        var stmt = """
        INSERT INTO status_logs
            (service_id, stats, time, date, message)
        VALUES (?, ?, ?, ?, ?)
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
                    insertStatement.setTime(3, java.sql.Time.valueOf(statusLog.checkedAtTime()));
                    insertStatement.setDate(4, java.sql.Date.valueOf(statusLog.checkedAtDate()));
                    insertStatement.setString(5, statusLog.message());

                    insertStatement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create status log", e);
        }
    }

    //

    private Connection getConnection() {
        if (connection == null) {
            throw new RuntimeException("Connection not opened");
        }
        return connection;
    }

    public StatusLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UnitOfWork begin() throws UnitOfWorkException {
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            return this;
        } catch (SQLException e) {
            throw new UnitOfWorkException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commit() throws UnitOfWorkException {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new UnitOfWorkException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollback() throws UnitOfWorkException {
        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new UnitOfWorkException("Failed to roll back transaction", e);
        }
    }

    @Override
    public void close() throws UnitOfWorkException {
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            throw new UnitOfWorkException("Failed to close connection", e);
        }
    }

}
