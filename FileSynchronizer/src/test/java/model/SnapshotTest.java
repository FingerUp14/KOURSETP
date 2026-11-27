package model;

import sync.model.DirectorySnapshot;
import sync.model.FileInfo;
import sync.util.FileScanner;
import sync.util.SafePaths;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SnapshotTest {
    private SnapshotTest() {}

    public static void run() throws Exception {
        Path root = Files.createTempDirectory("sync-snapshot-test-");
        try {
            Files.createDirectories(root.resolve("вложенный"));
            Files.writeString(root.resolve("вложенный/файл.txt"), "data");
            Files.writeString(root.resolve("a.txt"), "abc");
            Path log = Files.writeString(root.resolve("sync.log"), "log");
            Files.createDirectories(root.resolve(SafePaths.TEMP_DIRECTORY));
            Files.writeString(
                    root.resolve(SafePaths.TEMP_DIRECTORY).resolve("unfinished.part"), "x");
            DirectorySnapshot snapshot = FileScanner.scan(root, log);
            TestSupport.equal(2, snapshot.size());
            TestSupport.equal("a.txt", snapshot.getFiles().get(0).getRelativePath());
            FileInfo nested = snapshot.find("вложенный/файл.txt");
            TestSupport.check(nested != null && nested.getSize() == 4, "Nested file missing");
            TestSupport.throwsException(
                    IllegalArgumentException.class, () -> snapshot.addFile(nested));
            TestSupport.throwsException(
                    UnsupportedOperationException.class, () -> snapshot.getFiles().clear());
            TestSupport.throwsException(
                    java.io.IOException.class,
                    () -> FileScanner.scan(root.resolve("missing"), null));
        } finally {
            TestSupport.deleteTree(root);
        }
    }
}
