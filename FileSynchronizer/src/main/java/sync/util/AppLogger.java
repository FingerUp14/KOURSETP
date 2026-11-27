package sync.util;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.JTextArea;

public final class AppLogger {

    private static final String LOG_FILE = "sync.log";

    private static JTextArea logArea;

    private AppLogger() {
    }

    public static void setLogArea(JTextArea area) {
        logArea = area;
    }

    public static void log(String message) {

        String line =
                "[" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + "] "
                + message;

        if (logArea != null) {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }

        try (FileWriter writer = new FileWriter(LOG_FILE, true)) {

            writer.write(line);
            writer.write(System.lineSeparator());

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

}