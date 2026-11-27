package model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

public final class TestSupport {
    private TestSupport() {}

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    public static void throwsException(Class<? extends Throwable> type, CheckedAction action)
            throws Exception {
        try {
            action.run();
        } catch (Throwable e) {
            if (type.isInstance(e)) {
                return;
            }
            throw new AssertionError("Expected " + type.getName() + ", got " + e, e);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    public static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    public interface CheckedAction {
        void run() throws Exception;
    }
}
