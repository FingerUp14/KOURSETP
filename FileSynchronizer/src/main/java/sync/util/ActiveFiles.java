package sync.util;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Files being installed by the fallback used on filesystems without hard links. */
public final class ActiveFiles {
    private static final Set<Path> PATHS = ConcurrentHashMap.newKeySet();

    private ActiveFiles() {}

    public static void add(Path path) {
        PATHS.add(path);
    }

    public static void remove(Path path) {
        PATHS.remove(path);
    }

    public static boolean contains(Path path) {
        return PATHS.contains(path);
    }
}
