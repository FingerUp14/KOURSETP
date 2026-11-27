package sync.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Thread-safe text log shared by the server, client and user interface. */
public final class AppLogger {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path file;
    private final Consumer<String> listener;

    public AppLogger(Path file, Consumer<String> listener) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.listener = listener;
        Files.createDirectories(this.file.getParent());
        try (var ignored =
                Files.newBufferedWriter(
                        this.file,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND)) {
            // Fail at startup if the log cannot be written.
        }
    }

    public Path getFile() {
        return file;
    }

    public synchronized void log(String message) {
        String line = "[" + FORMAT.format(LocalDateTime.now()) + "] " + message;
        try {
            Files.writeString(
                    file,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Cannot write log " + file + ": " + e.getMessage());
        }
        System.out.println(line);
        if (listener != null) {
            listener.accept(line);
        }
    }
}
