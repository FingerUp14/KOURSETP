package sync.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Describes transfers and conflicts without changing either directory. */
public final class SyncPlan {
    private final List<FileInfo> downloads = new ArrayList<>();
    private final List<FileInfo> uploads = new ArrayList<>();
    private final List<Conflict> conflicts = new ArrayList<>();

    private SyncPlan() {}

    public static SyncPlan compare(DirectorySnapshot local, DirectorySnapshot remote) {
        SyncPlan plan = new SyncPlan();
        for (FileInfo remoteFile : remote.getFiles()) {
            FileInfo localFile = local.find(remoteFile.getRelativePath());
            if (localFile == null) {
                plan.downloads.add(remoteFile);
            } else if (localFile.getSize() != remoteFile.getSize()
                    || !localFile.getHash().equals(remoteFile.getHash())) {
                plan.conflicts.add(new Conflict(localFile, remoteFile));
            }
        }
        for (FileInfo localFile : local.getFiles()) {
            if (remote.find(localFile.getRelativePath()) == null) {
                plan.uploads.add(localFile);
            }
        }
        return plan;
    }

    public List<FileInfo> getDownloads() {
        return Collections.unmodifiableList(downloads);
    }

    public List<FileInfo> getUploads() {
        return Collections.unmodifiableList(uploads);
    }

    public List<Conflict> getConflicts() {
        return Collections.unmodifiableList(conflicts);
    }

    public static final class Conflict {
        private final FileInfo local;
        private final FileInfo remote;

        private Conflict(FileInfo local, FileInfo remote) {
            this.local = local;
            this.remote = remote;
        }

        public FileInfo getLocal() {
            return local;
        }

        public FileInfo getRemote() {
            return remote;
        }
    }
}
