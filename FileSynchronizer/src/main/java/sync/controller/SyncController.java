package sync.controller;

import sync.network.SyncClient;
import sync.network.SyncServer;
import sync.util.AppLogger;
import sync.util.SnapshotStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the local server and schedules outgoing synchronization attempts. */
public final class SyncController implements AutoCloseable {
    private final Path root;
    private final int localPort;
    private final String host;
    private final int remotePort;
    private final int intervalSeconds;
    private final AppLogger log;
    private final AtomicBoolean synchronizing = new AtomicBoolean();
    private volatile boolean running;
    private SyncServer server;
    private SyncClient client;
    private ScheduledExecutorService scheduler;

    public SyncController(
            Path root,
            int localPort,
            String host,
            int remotePort,
            int intervalSeconds,
            AppLogger log) {
        if (localPort < 1 || localPort > 65535 || remotePort < 1 || remotePort > 65535) {
            throw new IllegalArgumentException("Порт должен быть от 1 до 65535");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Укажите адрес второго компьютера");
        }
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("Интервал должен быть не меньше 1 секунды");
        }
        this.root = root.toAbsolutePath().normalize();
        this.localPort = localPort;
        this.host = host.trim();
        this.remotePort = remotePort;
        this.intervalSeconds = intervalSeconds;
        this.log = log;
    }

    public synchronized void start(boolean automatic) throws IOException {
        if (running) {
            throw new IllegalStateException("Already started");
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !Files.isWritable(root)) {
            throw new IOException("Каталог не существует или недоступен для записи: " + root);
        }
        int savedCount = SnapshotStore.load(root).size();
        log.log("Загружено записей сохранённого состояния: " + savedCount);
        server = new SyncServer(root, localPort, log);
        client = new SyncClient(root, log);
        running = true;
        server.start();
        if (automatic) {
            scheduleAutomatic(0);
        }
    }

    public void connect() throws IOException {
        if (!running) {
            throw new IOException("Сервер не запущен");
        }
        client.probe(host, remotePort);
        log.log("Подключение к " + host + ":" + remotePort + " установлено");
    }

    public synchronized void startAutomatic() {
        scheduleAutomatic(intervalSeconds);
    }

    private synchronized void scheduleAutomatic(int initialDelay) {
        if (!running || scheduler != null) {
            return;
        }
        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        task -> {
                            Thread thread = new Thread(task, "sync-scheduler");
                            thread.setDaemon(true);
                            return thread;
                        });
        scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        synchronizeNow();
                    } catch (IOException | RuntimeException e) {
                        if (running) {
                            log.log("Ошибка синхронизации: " + e.getMessage());
                        }
                    }
                },
                initialDelay,
                intervalSeconds,
                TimeUnit.SECONDS);
    }

    public String synchronizeNow() throws IOException {
        if (!running) {
            throw new IOException("Синхронизация не запущена");
        }
        if (!synchronizing.compareAndSet(false, true)) {
            return "Синхронизация уже выполняется";
        }
        try {
            return client.synchronize(host, remotePort);
        } finally {
            synchronizing.set(false);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!running) {
            return;
        }
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        IOException failure = null;
        try {
            client.close();
        } catch (IOException e) {
            failure = e;
        }
        try {
            server.close();
        } catch (IOException e) {
            failure = e;
        }
        if (failure != null) {
            throw failure;
        }
    }
}
