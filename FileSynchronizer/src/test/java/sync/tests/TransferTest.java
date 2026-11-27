package sync.tests;

import model.TestSupport;

import sync.model.FileInfo;
import sync.network.FileTransfer;
import sync.util.HashUtil;
import sync.util.SafePaths;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class TransferTest {
    private TransferTest() {}

    public static void run() throws Exception {
        Path root = Files.createTempDirectory("sync-transfer-test-");
        try {
            for (String invalid :
                    new String[] {
                        "../outside",
                        "/absolute",
                        "a/../../b",
                        "a\\b",
                        "C:/foo",
                        ".filesynchronizer-tmp/part"
                    }) {
                TestSupport.throwsException(
                        IOException.class, () -> SafePaths.resolve(root, invalid));
            }
            Path source = Files.writeString(root.resolve("source"), "correct");
            FileInfo info = new FileInfo("result.txt", 7, HashUtil.sha256(source));
            TestSupport.throwsException(
                    IOException.class, () -> FileTransfer.receive(input("invalid"), root, info));
            TestSupport.check(
                    !Files.exists(root.resolve("result.txt")), "Corrupt file was installed");
            Files.writeString(root.resolve("result.txt"), "keep me");
            TestSupport.equal(
                    FileTransfer.Result.CONFLICT,
                    FileTransfer.receive(input("correct"), root, info));
            TestSupport.equal("keep me", Files.readString(root.resolve("result.txt")));
            checkConcurrentReceives(root);
        } finally {
            TestSupport.deleteTree(root);
        }
    }

    private static void checkConcurrentReceives(Path root) throws Exception {
        Path alphaFile = Files.writeString(root.resolve("alpha-source"), "version A");
        Path betaFile = Files.writeString(root.resolve("beta-source"), "version B");
        FileInfo alpha = new FileInfo("race.txt", 9, HashUtil.sha256(alphaFile));
        FileInfo beta = new FileInfo("race.txt", 9, HashUtil.sha256(betaFile));
        var workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first =
                    workers.submit(
                            () -> {
                                start.await();
                                return FileTransfer.receive(input("version A"), root, alpha);
                            });
            var second =
                    workers.submit(
                            () -> {
                                start.await();
                                return FileTransfer.receive(input("version B"), root, beta);
                            });
            start.countDown();
            FileTransfer.Result firstResult = first.get(10, TimeUnit.SECONDS);
            FileTransfer.Result secondResult = second.get(10, TimeUnit.SECONDS);
            TestSupport.check(
                    (firstResult == FileTransfer.Result.COPIED
                                    && secondResult == FileTransfer.Result.CONFLICT)
                            || (firstResult == FileTransfer.Result.CONFLICT
                                    && secondResult == FileTransfer.Result.COPIED),
                    "Concurrent transfers must preserve the first file");
            String content = Files.readString(root.resolve("race.txt"));
            TestSupport.check(
                    content.equals("version A") || content.equals("version B"),
                    "Concurrent transfers damaged the file");
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static DataInputStream input(String content) throws Exception {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeBoolean(true);
            out.writeLong(bytes.length);
            out.write(bytes);
        }
        return new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
    }
}
