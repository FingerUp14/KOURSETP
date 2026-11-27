package sync.tests;

import model.HashTest;
import model.SnapshotTest;
import model.SyncPlanTest;

/** Executes tests with Java alone; a failure causes the Maven build to fail. */
public final class AllTests {
    private AllTests() {}

    public static void main(String[] args) throws Exception {
        run("HashTest", HashTest::run);
        run("SnapshotTest", SnapshotTest::run);
        run("SyncPlanTest", SyncPlanTest::run);
        run("TransferTest", TransferTest::run);
        run("IntegrationTest", IntegrationTest::run);
        System.out.println("All tests passed.");
    }

    private static void run(String name, model.TestSupport.CheckedAction test) throws Exception {
        test.run();
        System.out.println("PASS " + name);
    }
}
