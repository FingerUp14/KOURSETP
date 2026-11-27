package sync.util;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import sync.model.DirectorySnapshot;
import sync.model.FileInfo;

public final class FileScanner {

    private FileScanner() {
    }

    public static DirectorySnapshot scan(File root)
            throws IOException, NoSuchAlgorithmException {

        DirectorySnapshot snapshot = new DirectorySnapshot();

        scanRecursive(root, root, snapshot);

        return snapshot;

    }

    private static void scanRecursive(
            File root,
            File current,
            DirectorySnapshot snapshot)
            throws IOException, NoSuchAlgorithmException {

        File[] files = current.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {

                scanRecursive(root, file, snapshot);

            } else {

                String relative =
                        root.toPath()
                                .relativize(file.toPath())
                                .toString();

                snapshot.addFile(
                        new FileInfo(
                                relative,
                                file.length(),
                                HashUtil.sha256(file)
                        )
                );

            }

        }

    }

}