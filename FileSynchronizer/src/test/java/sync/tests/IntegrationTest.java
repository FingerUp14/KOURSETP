package sync.tests;

import model.TestSupport;

import sync.controller.SyncController;
import sync.network.SyncClient;
import sync.network.SyncServer;
import sync.util.AppLogger;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class IntegrationTest {
    private IntegrationTest() {}

    public static void run() throws Exception {
        Path base = Files.createTempDirectory("sync-network-test-");
        try {
            Path left = Files.createDirectory(base.resolve("left"));
            Path right = Files.createDirectory(base.resolve("right"));
            Files.createDirectories(left.resolve("nested"));
            byte[] binary = new byte[160_000];
            for (int i = 0; i < binary.length; i++) {
                binary[i] = (byte) (i * 31);
            }
            Files.write(left.resolve("nested/binary.bin"), binary);
            Files.write(right.resolve("empty.txt"), new byte[0]);
            Files.writeString(left.resolve("conflict.txt"), "left version");
            Files.writeString(right.resolve("conflict.txt"), "right version");
            AppLogger leftLog = new AppLogger(base.resolve("left.log"), null);
            AppLogger rightLog = new AppLogger(base.resolve("right.log"), null);
            try (SyncServer server = new SyncServer(right, 0, rightLog);
                    SyncClient client = new SyncClient(left, leftLog)) {
                server.start();
                String result = client.synchronize("127.0.0.1", server.getPort());
                TestSupport.check(result.contains("конфликтов 1"), "Conflict not counted");
                TestSupport.equal(0L, Files.size(left.resolve("empty.txt")));
                TestSupport.check(
                        Arrays.equals(
                                binary, Files.readAllBytes(right.resolve("nested/binary.bin"))),
                        "Binary file changed during transfer");
                TestSupport.equal("left version", Files.readString(left.resolve("conflict.txt")));
                TestSupport.equal("right version", Files.readString(right.resolve("conflict.txt")));
                client.synchronize("127.0.0.1", server.getPort());
            }
            String serverLog = Files.readString(base.resolve("right.log"));
            TestSupport.check(
                    serverLog.contains("127.0.0.1")
                            && serverLog.contains("binary.bin")
                            && serverLog.contains("КОНФЛИКТ"),
                    "Server log is incomplete");

            int leftPort = freePort();
            int rightPort = freePort();
            while (rightPort == leftPort) {
                rightPort = freePort();
            }
            try (SyncController first =
                            new SyncController(left, leftPort, "127.0.0.1", rightPort, 1, leftLog);
                    SyncController second =
                            new SyncController(
                                    right, rightPort, "127.0.0.1", leftPort, 1, rightLog)) {
                first.start(true);
                Thread.sleep(250);
                second.start(true);
                Files.writeString(left.resolve("later.txt"), "automatic");
                long deadline = System.nanoTime() + 12_000_000_000L;
                while (!Files.exists(right.resolve("later.txt")) && System.nanoTime() < deadline) {
                    Thread.sleep(100);
                }
                TestSupport.equal("automatic", Files.readString(right.resolve("later.txt")));
            }
        } finally {
            TestSupport.deleteTree(base);
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
