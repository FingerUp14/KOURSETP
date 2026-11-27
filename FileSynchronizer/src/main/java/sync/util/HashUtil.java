package sync.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    private HashUtil() {
    }

    public static String sha256(File file)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (FileInputStream input = new FileInputStream(file)) {

            byte[] buffer = new byte[8192];

            int count;

            while ((count = input.read(buffer)) != -1) {

                digest.update(buffer, 0, count);

            }

        }

        byte[] hash = digest.digest();

        StringBuilder builder = new StringBuilder();

        for (byte b : hash) {

            builder.append(String.format("%02x", b));

        }

        return builder.toString();

    }

}