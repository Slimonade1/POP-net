package dk.pop.status.temp.utils;

public interface UnitOfWork extends AutoCloseable {
    UnitOfWork begin() throws UnitOfWorkException;

    void commit() throws UnitOfWorkException;

    void rollback() throws UnitOfWorkException;

    @Override
    void close() throws UnitOfWorkException;

    class UnitOfWorkException extends Exception {
        public UnitOfWorkException(String message) {
            super(message);
        }

        public UnitOfWorkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
