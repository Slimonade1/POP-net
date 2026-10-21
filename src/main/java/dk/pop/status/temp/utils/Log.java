package dk.pop.status.temp.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {
    private Log() {
    }

    public static Logger getLogger(Class<?> type) {
        return LoggerFactory.getLogger(type);
    }

    public static RuntimeException giveUp(Logger logger, Throwable cause, String message) {
        logger.atError().setCause(cause).log(message);
        return new RuntimeException(message, cause);
    }

    public static RuntimeException giveUp(Logger logger, Throwable cause) {
        return giveUp(logger, cause, "Fatal error");
    }
}
