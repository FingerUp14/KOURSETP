package model;

import sync.model.DirectorySnapshot;
import sync.model.FileInfo;
import sync.model.SyncPlan;

public final class SyncPlanTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private SyncPlanTest() {}

    public static void run() throws Exception {
        DirectorySnapshot left = new DirectorySnapshot();
        DirectorySnapshot right = new DirectorySnapshot();
        left.addFile(new FileInfo("same.txt", 4, HASH_A));
        right.addFile(new FileInfo("same.txt", 4, HASH_A));
        left.addFile(new FileInfo("left.txt", 1, HASH_A));
        right.addFile(new FileInfo("dir/right.txt", 2, HASH_B));
        left.addFile(new FileInfo("conflict.txt", 3, HASH_A));
        right.addFile(new FileInfo("conflict.txt", 3, HASH_B));
        SyncPlan plan = SyncPlan.compare(left, right);
        TestSupport.equal(1, plan.getDownloads().size());
        TestSupport.equal("dir/right.txt", plan.getDownloads().get(0).getRelativePath());
        TestSupport.equal("left.txt", plan.getUploads().get(0).getRelativePath());
        TestSupport.equal("conflict.txt", plan.getConflicts().get(0).getLocal().getRelativePath());
        TestSupport.equal(HASH_B, plan.getConflicts().get(0).getRemote().getHash());
        TestSupport.equal(3, left.size());
        TestSupport.equal(3, right.size());
        TestSupport.throwsException(
                IllegalArgumentException.class, () -> new FileInfo("bad", -1, HASH_A));
        TestSupport.throwsException(
                IllegalArgumentException.class, () -> new FileInfo("bad", 1, "not a hash"));
    }
}
