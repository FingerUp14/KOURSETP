package sync;

import sync.controller.SyncController;
import sync.util.AppLogger;
import sync.view.MainWindow;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        // Redirected output (for example, in PowerShell 7) is UTF-8 even on JDK 17.
        if (System.console() == null) {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        }
        if (args.length == 0) {
            SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
            return;
        }
        try {
            runCommandLine(args);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.err.println("Справка: java -jar FileSynchronizer-1.0.jar --help");
            System.exit(1);
        }
    }

    private static void runCommandLine(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println(
                    "java -jar FileSynchronizer-1.0.jar --directory КАТАЛОГ "
                            + "--local-port ПОРТ --peer АДРЕС --peer-port ПОРТ "
                            + "[--interval СЕКУНДЫ] [--log ФАЙЛ] [--once]");
            System.out.println("Без аргументов запускается графический интерфейс.");
            return;
        }
        Set<String> names =
                Set.of(
                        "--directory",
                        "--local-port",
                        "--peer",
                        "--peer-port",
                        "--interval",
                        "--log");
        Map<String, String> options = new HashMap<>();
        boolean once = false;
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (option.equals("--once") && !once) {
                once = true;
            } else if (names.contains(option)
                    && i + 1 < args.length
                    && !options.containsKey(option)) {
                options.put(option, args[++i]);
            } else {
                throw new IllegalArgumentException("Неизвестный или повторный параметр: " + option);
            }
        }
        int localPort = Integer.parseInt(required(options, "--local-port"));
        Path root = Path.of(required(options, "--directory"));
        String peer = required(options, "--peer");
        int peerPort = Integer.parseInt(required(options, "--peer-port"));
        int interval = Integer.parseInt(options.getOrDefault("--interval", "10"));
        Path logPath = Path.of(options.getOrDefault("--log", "sync.log"));
        AppLogger log = new AppLogger(logPath, null);
        SyncController controller =
                new SyncController(root, localPort, peer, peerPort, interval, log);
        controller.start(!once);
        if (once) {
            try (controller) {
                controller.synchronizeNow();
            }
        } else {
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        try {
                                            controller.close();
                                        } catch (Exception e) {
                                            System.err.println(e.getMessage());
                                        }
                                    }));
            new CountDownLatch(1).await();
        }
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Не указан параметр " + key);
        }
        return value;
    }
}
