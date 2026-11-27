package model;

import sync.util.HashUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HashTest {
    private HashTest() {}

    public static void run() throws Exception {
        Path directory = Files.createTempDirectory("sync-hash-test-");
        try {
            Path file = directory.resolve("test.txt");
            Files.writeString(file, "abc", StandardCharsets.UTF_8);
            TestSupport.equal(
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    HashUtil.sha256(file));
            Files.write(file, new byte[0]);
            TestSupport.equal(
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    HashUtil.sha256(file));
        } finally {
            TestSupport.deleteTree(directory);
        }
    }
}
