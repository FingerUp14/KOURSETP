package sync.util;

import sync.model.DirectorySnapshot;
import sync.model.FileInfo;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileScanner {
    private FileScanner() {}

    public static DirectorySnapshot scan(Path directory, Path excludedLog) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Directory does not exist or is a symbolic link: " + root);
        }
        Path log = excludedLog == null ? null : excludedLog.toAbsolutePath().normalize();
        DirectorySnapshot snapshot = new DirectorySnapshot();
        Files.walkFileTree(
                root,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(root)
                                && root.relativize(dir).getNameCount() == 1
                                && dir.getFileName()
                                        .toString()
                                        .equalsIgnoreCase(SafePaths.TEMP_DIRECTORY)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (attrs.isRegularFile()
                                && !file.equals(log)
                                && !ActiveFiles.contains(file)) {
                            String relative =
                                    root.relativize(file)
                                            .toString()
                                            .replace(file.getFileSystem().getSeparator(), "/");
                            SafePaths.validate(relative);
                            String hash = HashUtil.sha256(file);
                            long size = Files.size(file);
                            if (size != attrs.size()) {
                                throw new IOException("File changed while scanning: " + relative);
                            }
                            snapshot.addFile(new FileInfo(relative, size, hash));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        SnapshotStore.save(root, snapshot);
        return snapshot;
    }
}
