package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusNote;
import dk.pop.status.temp.utils.UnitOfWork;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.annotation.RequestScope;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequestScope
public class StatusNoteRepository implements UnitOfWork {
    private final DataSource dataSource;
    private Connection connection;

    public StatusNoteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<StatusNote> findAll() {
        var sql = "SELECT id, description, service_id FROM status_notes ORDER BY id";
        try (var statement = getConnection().prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            List<StatusNote> notes = new ArrayList<>();
            while (resultSet.next()) {
                notes.add(readNote(resultSet));
            }
            return notes;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find status notes", e);
        }
    }

    public Optional<StatusNote> findById(long id) {
        var sql = "SELECT id, description, service_id FROM status_notes WHERE id = ?";
        try (var statement = getConnection().prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readNote(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find status note", e);
        }
    }

    public StatusNote create(StatusNote note) {
        var sql = "INSERT INTO status_notes (description, service_id) VALUES (?, ?)";
        try (var statement = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, note.description());
            statement.setLong(2, note.serviceId());
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Creating a status note did not return an ID");
                }
                return new StatusNote(keys.getLong(1), note.description(), note.serviceId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create status note", e);
        }
    }

    public boolean update(StatusNote note) {
        var sql = "UPDATE status_notes SET description = ?, service_id = ? WHERE id = ?";
        try (var statement = getConnection().prepareStatement(sql)) {
            statement.setString(1, note.description());
            statement.setLong(2, note.serviceId());
            statement.setLong(3, note.id());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update status note", e);
        }
    }

    public boolean delete(long id) {
        var sql = "DELETE FROM status_notes WHERE id = ?";
        try (var statement = getConnection().prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete status note", e);
        }
    }

    private static StatusNote readNote(java.sql.ResultSet resultSet) throws SQLException {
        return new StatusNote(resultSet.getLong("id"), resultSet.getString("description"),
                resultSet.getLong("service_id"));
    }

    private Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("Connection not opened");
        }
        return connection;
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
