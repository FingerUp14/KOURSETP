package sync.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A deterministic index of files observed in a directory. */
public final class DirectorySnapshot {
    private final Map<String, FileInfo> files = new TreeMap<>();

    public void addFile(FileInfo file) {
        if (files.putIfAbsent(file.getRelativePath(), file) != null) {
            throw new IllegalArgumentException("Duplicate path: " + file.getRelativePath());
        }
    }

    public List<FileInfo> getFiles() {
        return Collections.unmodifiableList(new ArrayList<>(files.values()));
    }

    public int size() {
        return files.size();
    }

    public FileInfo find(String relativePath) {
        return files.get(relativePath);
    }
}
