package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.StatusNote;
import dk.pop.status.temp.repository.StatusNoteRepository;
import dk.pop.status.temp.utils.UnitOfWork;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class StatusNoteService {
    private static final int MAX_DESCRIPTION_LENGTH = 511;
    private final StatusNoteRepository repository;

    public StatusNoteService(StatusNoteRepository repository) {
        this.repository = repository;
    }

    public List<StatusNote> findAll() {
        return inTransaction(repository::findAll);
    }

    public Optional<StatusNote> findById(long id) {
        return inTransaction(() -> repository.findById(id));
    }

    public StatusNote create(String description, long serviceId) {
        return inTransaction(() -> repository.create(new StatusNote(null, normalize(description), serviceId)));
    }

    public boolean update(long id, String description, long serviceId) {
        return inTransaction(() -> repository.update(new StatusNote(id, normalize(description), serviceId)));
    }

    public boolean delete(long id) {
        return inTransaction(() -> repository.delete(id));
    }

    private static String normalize(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A description is required.");
        }
        String normalized = description.strip();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Descriptions must be " + MAX_DESCRIPTION_LENGTH + " characters or fewer.");
        }
        return normalized;
    }

    private <T> T inTransaction(Supplier<T> operation) {
        try (UnitOfWork transaction = repository.begin()) {
            try {
                T result = operation.get();
                transaction.commit();
                return result;
            } catch (UnitOfWork.UnitOfWorkException e) {
                rollback(transaction, e);
                throw new IllegalStateException("Status note transaction failed", e);
            } catch (RuntimeException e) {
                rollback(transaction, e);
                throw e;
            }
        } catch (UnitOfWork.UnitOfWorkException e) {
            throw new IllegalStateException("Status note transaction failed", e);
        }
    }

    private static void rollback(UnitOfWork transaction, Exception originalFailure) {
        try {
            transaction.rollback();
        } catch (UnitOfWork.UnitOfWorkException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }
}
