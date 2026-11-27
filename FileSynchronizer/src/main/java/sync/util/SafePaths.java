package sync.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Validates paths received from the network before accessing the filesystem. */
public final class SafePaths {
    public static final String TEMP_DIRECTORY = ".filesynchronizer-tmp";

    private SafePaths() {}

    public static void validate(String relative) throws IOException {
        if (relative == null
                || relative.isEmpty()
                || relative.startsWith("/")
                || relative.indexOf('\\') >= 0
                || relative.indexOf(':') >= 0
                || relative.indexOf('\0') >= 0) {
            throw new IOException("Invalid relative path: " + relative);
        }
        String[] parts = relative.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("Invalid relative path: " + relative);
            }
        }
        if (parts[0].equalsIgnoreCase(TEMP_DIRECTORY)) {
            throw new IOException("Reserved path: " + relative);
        }
    }

    public static Path resolve(Path root, String relative) throws IOException {
        validate(relative);
        Path base = root.toAbsolutePath().normalize();
        Path result = base.resolve(relative).normalize();
        if (!result.startsWith(base) || result.equals(base)) {
            throw new IOException("Path leaves the synchronization directory: " + relative);
        }
        Path current = base;
        for (Path part : base.relativize(result)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not synchronized: " + current);
            }
        }
        return result;
    }

    public static Path createParentDirectories(Path root, String relative) throws IOException {
        Path target = resolve(root, relative);
        Path base = root.toAbsolutePath().normalize();
        Path current = base;
        for (Path part : base.relativize(target.getParent())) {
            current = current.resolve(part);
            try {
                Files.createDirectory(current);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Not a directory: " + current, e);
                }
            }
        }
        return resolve(root, relative);
    }
}
