package sync.network;

import sync.model.DirectorySnapshot;
import sync.model.FileInfo;
import sync.model.SyncPlan;
import sync.util.AppLogger;
import sync.util.FileScanner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Accepts incoming synchronization sessions until closed. */
public final class SyncServer implements AutoCloseable {
    private final Path root;
    private final AppLogger log;
    private final ServerSocket serverSocket;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final Thread acceptThread;

    public SyncServer(Path root, int port, AppLogger log) throws IOException {
        this.root = root;
        this.log = log;
        this.serverSocket = new ServerSocket(port);
        this.acceptThread = new Thread(this::acceptLoop, "sync-accept");
        this.acceptThread.setDaemon(true);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void start() {
        log.log("Сервер запущен, порт " + getPort() + ", каталог " + root);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                connections.add(socket);
                workers.execute(() -> handle(socket));
            } catch (SocketException e) {
                if (!serverSocket.isClosed()) {
                    log.log("Ошибка сервера: " + e.getMessage());
                }
            } catch (IOException | RuntimeException e) {
                if (!serverSocket.isClosed()) {
                    log.log("Ошибка сервера: " + e.getMessage());
                }
            }
        }
    }

    private void handle(Socket socket) {
        String peer = socket.getRemoteSocketAddress().toString();
        log.log("Подключился клиент " + peer);
        try (socket;
                DataInputStream in =
                        new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out =
                        new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            socket.setSoTimeout(30000);
            String magic = in.readUTF();
            if (Protocol.PING.equals(magic)) {
                out.writeUTF(Protocol.PING);
                out.flush();
                return;
            }
            if (!Protocol.MAGIC.equals(magic)) {
                throw new IOException("Unknown protocol");
            }
            DirectorySnapshot remote = Protocol.readSnapshot(in);
            DirectorySnapshot local = FileScanner.scan(root, log.getFile());
            Protocol.writeSnapshot(out, local);
            SyncPlan plan = SyncPlan.compare(local, remote);
            Protocol.logConflicts(plan, log, peer);
            while (true) {
                int command = in.readUnsignedByte();
                if (command == Protocol.END) {
                    break;
                }
                String path = in.readUTF();
                if (command == Protocol.GET) {
                    FileInfo file = local.find(path);
                    if (file == null || remote.find(path) != null) {
                        throw new IOException("Unexpected download: " + path);
                    }
                    boolean sent = FileTransfer.send(out, root, file);
                    log.log(
                            (sent ? "Отправлен клиенту " : "Не отправлен клиенту ")
                                    + peer
                                    + ": "
                                    + path);
                } else if (command == Protocol.PUT) {
                    FileInfo file = remote.find(path);
                    if (file == null || local.find(path) != null) {
                        throw new IOException("Unexpected upload: " + path);
                    }
                    out.writeBoolean(true);
                    out.flush();
                    FileTransfer.Result result = FileTransfer.receive(in, root, file);
                    log.log("Получение от " + peer + ": " + path + " — " + result);
                    if (result == FileTransfer.Result.CONFLICT) {
                        log.log(
                                "КОНФЛИКТ с "
                                        + peer
                                        + ": "
                                        + path
                                        + " появился во время передачи; существующая версия"
                                        + " сохранена");
                    }
                    out.writeUTF(result.name());
                    out.flush();
                } else {
                    throw new IOException("Unknown command: " + command);
                }
            }
            FileScanner.scan(root, log.getFile());
            log.log("Сеанс клиента " + peer + " завершён");
        } catch (IOException | RuntimeException e) {
            log.log("Ошибка клиента " + peer + ": " + e.getMessage());
        } finally {
            connections.remove(socket);
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        for (Socket connection : connections) {
            connection.close();
        }
        workers.shutdownNow();
        try {
            workers.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.log("Сервер остановлен");
    }
}
