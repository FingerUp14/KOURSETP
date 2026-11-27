package sync.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import model.HashTest;
import model.SnapshotTest;
import model.SyncPlanTest;
import model.TestSupport;

import org.junit.jupiter.api.Test;

import sync.model.DirectorySnapshot;
import sync.network.SyncClient;
import sync.network.SyncServer;
import sync.util.AppLogger;
import sync.util.FileScanner;
import sync.util.SnapshotStore;

import java.nio.file.Files;
import java.nio.file.Path;

class ModelAndIntegrationTest {
    @Test
    void hash() throws Exception {
        HashTest.run();
    }

    @Test
    void snapshot() throws Exception {
        SnapshotTest.run();
    }

    @Test
    void plan() throws Exception {
        SyncPlanTest.run();
    }

    @Test
    void transfer() throws Exception {
        TransferTest.run();
    }

    @Test
    void integration() throws Exception {
        IntegrationTest.run();
    }

    @Test
    void connectionChecksProtocol() throws Exception {
        Path root = Files.createTempDirectory("connection-test-");
        try {
            AppLogger logger = new AppLogger(root.resolve("sync.log"), null);
            try (SyncServer server = new SyncServer(root, 0, logger);
                    SyncClient client = new SyncClient(root, logger)) {
                server.start();
                client.probe("127.0.0.1", server.getPort());
            }
        } finally {
            TestSupport.deleteTree(root);
        }
    }

    @Test
    void savedSnapshotIsRestoredAndUpdated() throws Exception {
        Path root = Files.createTempDirectory("saved-snapshot-");
        try {
            Files.writeString(root.resolve("файл.txt"), "первый");
            DirectorySnapshot original = FileScanner.scan(root, null);
            assertEquals(original.getFiles(), SnapshotStore.load(root).getFiles());
            Files.writeString(root.resolve("другой.txt"), "второй");
            FileScanner.scan(root, null);
            assertEquals(2, SnapshotStore.load(root).size());
            Files.writeString(
                    root.resolve(".filesynchronizer-tmp/snapshot.properties"), "count=-1");
            assertThrows(java.io.IOException.class, () -> SnapshotStore.load(root));
        } finally {
            TestSupport.deleteTree(root);
        }
    }
}
