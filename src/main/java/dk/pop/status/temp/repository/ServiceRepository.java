package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.Service;
import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.utils.UnitOfWork;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class ServiceRepository implements UnitOfWork {
    private final DataSource dataSource;
    private Connection connection;

    public void create(Service service) {

        var stmt = """
        INSERT INTO services (name, description)
        VALUES (?,?)
        """;

        try(var statement = getConnection().prepareStatement(stmt)){

            statement.setString(1, service.name());
            statement.setString(2, service.description());

            statement.executeUpdate();

        } catch (java.sql.SQLException e){

        }
    }

    public void updateDescription(String serviceName, String newDescription){

        var stmt = """
                UPDATE services
                SET description = ?
                WHERE name = ?;        
                """;

        try(var statement = getConnection().prepareStatement(stmt)){

            statement.setString(1, newDescription);
            statement.setString(2, serviceName);

            statement.executeUpdate();

        } catch (SQLException e) {

        }
    }

    //

    private Connection getConnection() {
        if (connection == null) {
            throw new RuntimeException("Connection not opened");
        }
        return connection;
    }

    public ServiceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UnitOfWork begin() throws UnitOfWork.UnitOfWorkException {
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            return this;
        } catch (SQLException e) {
            throw new UnitOfWork.UnitOfWorkException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commit() throws UnitOfWork.UnitOfWorkException {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new UnitOfWork.UnitOfWorkException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollback() throws UnitOfWork.UnitOfWorkException {
        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new UnitOfWork.UnitOfWorkException("Failed to roll back transaction", e);
        }
    }

    @Override
    public void close() throws UnitOfWork.UnitOfWorkException {
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            throw new UnitOfWork.UnitOfWorkException("Failed to close connection", e);
        }
    }

}
