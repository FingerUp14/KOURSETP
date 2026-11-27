package sync.network;

import sync.model.DirectorySnapshot;
import sync.model.FileInfo;
import sync.model.SyncPlan;
import sync.util.AppLogger;
import sync.util.SafePaths;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Length-delimited protocol; Java object deserialization is deliberately not used. */
final class Protocol {
    static final String MAGIC = "FILE-SYNC-1";
    static final int GET = 1;
    static final int PUT = 2;
    static final int END = 3;
    private static final int MAX_FILES = 100_000;

    private Protocol() {}

    static void writeSnapshot(DataOutputStream out, DirectorySnapshot snapshot) throws IOException {
        if (snapshot.size() > MAX_FILES) {
            throw new IOException("Too many files in directory");
        }
        out.writeInt(snapshot.size());
        for (FileInfo file : snapshot.getFiles()) {
            SafePaths.validate(file.getRelativePath());
            out.writeUTF(file.getRelativePath());
            out.writeLong(file.getSize());
            out.writeUTF(file.getHash());
        }
        out.flush();
    }

    static DirectorySnapshot readSnapshot(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_FILES) {
            throw new IOException("Invalid file count: " + count);
        }
        DirectorySnapshot snapshot = new DirectorySnapshot();
        for (int i = 0; i < count; i++) {
            String path = in.readUTF();
            SafePaths.validate(path);
            long size = in.readLong();
            String hash = in.readUTF();
            try {
                snapshot.addFile(new FileInfo(path, size, hash));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid snapshot entry: " + path, e);
            }
        }
        return snapshot;
    }

    static void logConflicts(SyncPlan plan, AppLogger log, String peer) {
        for (SyncPlan.Conflict conflict : plan.getConflicts()) {
            log.log(
                    "КОНФЛИКТ с "
                            + peer
                            + ": "
                            + conflict.getLocal().getRelativePath()
                            + "; локальный SHA-256="
                            + conflict.getLocal().getHash()
                            + "; удалённый SHA-256="
                            + conflict.getRemote().getHash());
        }
    }
}
