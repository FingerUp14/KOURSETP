package sync.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectorySnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<FileInfo> files = new ArrayList<>();

    public void addFile(FileInfo file) {
        files.add(file);
    }

    public List<FileInfo> getFiles() {
        return Collections.unmodifiableList(files);
    }

    public int size() {
        return files.size();
    }

    public FileInfo find(String relativePath) {

        for (FileInfo file : files) {

            if (file.getRelativePath().equals(relativePath)) {
                return file;
            }

        }

        return null;

    }

}