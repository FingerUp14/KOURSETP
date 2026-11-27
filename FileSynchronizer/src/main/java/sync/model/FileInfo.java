package sync.model;

import java.util.Objects;

/** Metadata for one file, relative to the synchronization directory. */
public final class FileInfo {
    private final String relativePath;
    private final long size;
    private final String hash;

    public FileInfo(String relativePath, long size, String hash) {
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.hash = Objects.requireNonNull(hash, "hash");
        if (relativePath.isBlank() || size < 0 || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid file metadata: " + relativePath);
        }
        this.size = size;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public long getSize() {
        return size;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FileInfo)) {
            return false;
        }
        FileInfo file = (FileInfo) other;
        return size == file.size
                && relativePath.equals(file.relativePath)
                && hash.equals(file.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath, size, hash);
    }
}
