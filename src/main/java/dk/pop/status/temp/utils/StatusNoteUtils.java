package dk.pop.status.temp.utils;

public final class StatusNoteUtils {
    public static final int MAX_NOTE_LENGTH = 511;

    private StatusNoteUtils() {
    }

    public static String normalize(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("A public note is required.");
        }
        String normalized = description.strip();
        if (normalized.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Public notes must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }
        return normalized;
    }
}
