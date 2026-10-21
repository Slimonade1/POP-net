package dk.pop.status.temp.utils;

public final class StatusMessageUtils {
    public static final int MAX_MESSAGE_LENGTH = 200;

    private StatusMessageUtils() {
    }

    public static String normalize(String message) {
        Assert.isNotNull(message, "A message is required.");

        String normalized = message.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A message is required.");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "Messages must be " + MAX_MESSAGE_LENGTH + " characters or fewer.");
        }

        return normalized;
    }
}
