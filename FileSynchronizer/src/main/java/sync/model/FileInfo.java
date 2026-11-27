package sync.model;

import java.io.Serializable;
import java.util.Objects;

public class FileInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String relativePath;
    private final long size;
    private final String hash;

    public FileInfo(String relativePath, long size, String hash) {
        this.relativePath = relativePath;
        this.size = size;
        this.hash = hash;
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
    public String toString() {
        return relativePath +
                " | " +
                size +
                " bytes";
    }

    @Override
    public boolean equals(Object o) {

        if (this == o)
            return true;

        if (!(o instanceof FileInfo))
            return false;

        FileInfo other = (FileInfo) o;

        return Objects.equals(relativePath, other.relativePath)
                && Objects.equals(hash, other.hash);

    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath, hash);
    }

}