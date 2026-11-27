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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;

public final class SyncClient implements AutoCloseable {
    private final Path root;
    private final AppLogger log;
    private Socket currentSocket;
    private boolean closed;

    public SyncClient(Path root, AppLogger log) {
        this.root = root;
        this.log = log;
    }

    public String synchronize(String host, int port) throws IOException {
        DirectorySnapshot local = FileScanner.scan(root, log.getFile());
        try (Socket socket = new Socket()) {
            synchronized (this) {
                if (closed) {
                    throw new IOException("Client is stopped");
                }
                currentSocket = socket;
            }
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(30000);
            String peer = socket.getRemoteSocketAddress().toString();
            log.log("Соединение с " + peer + "; начало синхронизации");
            try (DataOutputStream out =
                            new DataOutputStream(
                                    new BufferedOutputStream(socket.getOutputStream()));
                    DataInputStream in =
                            new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
                out.writeUTF(Protocol.MAGIC);
                Protocol.writeSnapshot(out, local);
                DirectorySnapshot remote = Protocol.readSnapshot(in);
                SyncPlan plan = SyncPlan.compare(local, remote);
                Protocol.logConflicts(plan, log, peer);
                int downloaded = 0;
                int uploaded = 0;
                int conflicts = plan.getConflicts().size();
                for (FileInfo file : plan.getDownloads()) {
                    out.writeByte(Protocol.GET);
                    out.writeUTF(file.getRelativePath());
                    out.flush();
                    FileTransfer.Result result = FileTransfer.receive(in, root, file);
                    if (result == FileTransfer.Result.COPIED) {
                        downloaded++;
                    }
                    if (result == FileTransfer.Result.CONFLICT) {
                        conflicts++;
                        log.log(
                                "КОНФЛИКТ: "
                                        + file.getRelativePath()
                                        + " появился во время передачи; существующая версия"
                                        + " сохранена");
                    }
                    log.log("Получен от " + peer + ": " + file.getRelativePath() + " — " + result);
                }
                for (FileInfo file : plan.getUploads()) {
                    out.writeByte(Protocol.PUT);
                    out.writeUTF(file.getRelativePath());
                    out.flush();
                    if (!in.readBoolean()) {
                        throw new IOException("Upload refused");
                    }
                    if (!FileTransfer.send(out, root, file)) {
                        throw new IOException("Файл изменился: " + file.getRelativePath());
                    }
                    FileTransfer.Result result = FileTransfer.Result.valueOf(in.readUTF());
                    if (result == FileTransfer.Result.COPIED) {
                        uploaded++;
                    }
                    if (result == FileTransfer.Result.CONFLICT) {
                        conflicts++;
                        log.log(
                                "КОНФЛИКТ: "
                                        + file.getRelativePath()
                                        + " уже существует у "
                                        + peer
                                        + "; версия партнёра сохранена");
                    }
                    log.log("Отправлен " + peer + ": " + file.getRelativePath() + " — " + result);
                }
                out.writeByte(Protocol.END);
                out.flush();
                String summary =
                        "Завершено: получено "
                                + downloaded
                                + ", отправлено "
                                + uploaded
                                + ", конфликтов "
                                + conflicts;
                log.log(summary);
                return summary;
            }
        } finally {
            synchronized (this) {
                currentSocket = null;
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        closed = true;
        if (currentSocket != null) {
            currentSocket.close();
        }
    }
}
