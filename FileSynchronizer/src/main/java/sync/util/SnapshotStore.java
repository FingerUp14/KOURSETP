package sync.util;

import sync.model.DirectorySnapshot;
import sync.model.FileInfo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Saves and loads the last observed directory snapshot as UTF-8 text. */
public final class SnapshotStore {
    private SnapshotStore() {}

    public static synchronized DirectorySnapshot load(Path root) throws IOException {
        Path file = stateFile(root);
        DirectorySnapshot snapshot = new DirectorySnapshot();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return snapshot;
        }
        if (Files.isSymbolicLink(file)) {
            throw new IOException("Файл состояния является символической ссылкой: " + file);
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        try {
            int count = Integer.parseInt(properties.getProperty("count"));
            if (count < 0 || count > 100_000) {
                throw new IllegalArgumentException("Invalid count");
            }
            for (int i = 0; i < count; i++) {
                String prefix = "file." + i + ".";
                String path = properties.getProperty(prefix + "path");
                SafePaths.validate(path);
                snapshot.addFile(
                        new FileInfo(
                                path,
                                Long.parseLong(properties.getProperty(prefix + "size")),
                                properties.getProperty(prefix + "sha256")));
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IOException("Повреждён файл состояния: " + file, e);
        }
        return snapshot;
    }

    public static synchronized void save(Path root, DirectorySnapshot snapshot) throws IOException {
        Path file = stateFile(root);
        Path parent = file.getParent();
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent) || Files.isSymbolicLink(file)) {
            throw new IOException("Недопустимая ссылка в пути к файлу состояния");
        }
        Properties properties = new Properties();
        properties.setProperty("count", Integer.toString(snapshot.size()));
        int i = 0;
        for (FileInfo info : snapshot.getFiles()) {
            String prefix = "file." + i++ + ".";
            properties.setProperty(prefix + "path", info.getRelativePath());
            properties.setProperty(prefix + "size", Long.toString(info.getSize()));
            properties.setProperty(prefix + "sha256", info.getHash());
        }
        Path temp = Files.createTempFile(parent, "snapshot-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, "File Synchronizer snapshot");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Path stateFile(Path root) throws IOException {
        Path parent = root.toAbsolutePath().normalize().resolve(SafePaths.TEMP_DIRECTORY);
        if (Files.isSymbolicLink(parent)) {
            throw new IOException("Каталог состояния является символической ссылкой");
        }
        return parent.resolve("snapshot.properties");
    }
}
