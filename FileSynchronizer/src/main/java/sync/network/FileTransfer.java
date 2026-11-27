package sync.network;

import sync.model.FileInfo;
import sync.util.ActiveFiles;
import sync.util.HashUtil;
import sync.util.SafePaths;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Streams and verifies files without overwriting an existing destination. */
public final class FileTransfer {
    public enum Result {
        COPIED,
        UNCHANGED,
        CONFLICT
    }

    private static final Object FALLBACK_LOCK = new Object();

    private FileTransfer() {}

    public static boolean send(DataOutputStream out, Path root, FileInfo expected)
            throws IOException {
        Path source;
        try {
            source = SafePaths.resolve(root, expected.getRelativePath());
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(source) != expected.getSize()
                    || !HashUtil.sha256(source).equals(expected.getHash())) {
                throw new IOException(
                        "Файл изменился после сканирования: " + expected.getRelativePath());
            }
        } catch (IOException e) {
            out.writeBoolean(false);
            out.writeUTF(e.getMessage());
            out.flush();
            return false;
        }

        out.writeBoolean(true);
        out.writeLong(expected.getSize());
        try (InputStream file = Files.newInputStream(source)) {
            byte[] buffer = new byte[65536];
            long remaining = expected.getSize();
            while (remaining > 0) {
                int count = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) {
                    throw new IOException("File was truncated during transfer");
                }
                out.write(buffer, 0, count);
                remaining -= count;
            }
        }
        out.flush();
        return true;
    }

    public static Result receive(DataInputStream in, Path root, FileInfo expected)
            throws IOException {
        if (!in.readBoolean()) {
            throw new IOException(in.readUTF());
        }
        long length = in.readLong();
        if (length != expected.getSize()) {
            throw new IOException("Invalid transfer size for " + expected.getRelativePath());
        }
        Path base = root.toAbsolutePath().normalize();
        Path tempDirectory = base.resolve(SafePaths.TEMP_DIRECTORY);
        if (Files.isSymbolicLink(tempDirectory)) {
            throw new IOException("Temporary directory is a symbolic link");
        }
        Files.createDirectories(tempDirectory);
        if (!Files.isDirectory(tempDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid temporary directory");
        }
        Path temporary = Files.createTempFile(tempDirectory, "transfer-", ".part");
        try {
            MessageDigest digest = HashUtil.newSha256();
            try (OutputStream file = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                long remaining = length;
                while (remaining > 0) {
                    int count = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (count < 0) {
                        throw new IOException("Incomplete file transfer");
                    }
                    file.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    remaining -= count;
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(expected.getHash())) {
                throw new IOException("SHA-256 mismatch: " + expected.getRelativePath());
            }
            Path target = SafePaths.createParentDirectories(base, expected.getRelativePath());
            try {
                // Creating a hard link is atomic and cannot replace an existing file.
                Files.createLink(target, temporary);
                return Result.COPIED;
            } catch (FileAlreadyExistsException e) {
                return compareExisting(target, expected);
            } catch (UnsupportedOperationException | IOException e) {
                // FAT and some network shares cannot create hard links. CREATE_NEW
                // still protects an existing file; the scanner skips an active copy.
                return copyWithoutReplacing(temporary, target, expected);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Result copyWithoutReplacing(Path temporary, Path target, FileInfo expected)
            throws IOException {
        synchronized (FALLBACK_LOCK) {
            ActiveFiles.add(target);
            boolean created = false;
            try {
                try (OutputStream out =
                        Files.newOutputStream(
                                target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    created = true;
                    Files.copy(temporary, out);
                }
                return Result.COPIED;
            } catch (FileAlreadyExistsException e) {
                return compareExisting(target, expected);
            } catch (IOException e) {
                if (created) {
                    Files.deleteIfExists(target);
                }
                throw e;
            } finally {
                ActiveFiles.remove(target);
            }
        }
    }

    private static Result compareExisting(Path target, FileInfo expected) throws IOException {
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && Files.size(target) == expected.getSize()
                && HashUtil.sha256(target).equals(expected.getHash())) {
            return Result.UNCHANGED;
        }
        return Result.CONFLICT;
    }
}
