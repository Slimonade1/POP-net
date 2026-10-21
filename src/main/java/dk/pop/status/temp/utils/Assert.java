package dk.pop.status.temp.utils;

public final class Assert {
    private Assert() {
    }

    public static void isNotNull(Object expected, String message) {
        if (expected == null) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void isTrue(boolean expected, String message) {
        if (!expected) {
            throw new AssertionError(message);
        }
    }

    public static void isNull(Object expected, String message) {
        if (expected != null) {
            throw new IllegalArgumentException(message);
        }
    }
}
