package sync.network;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import sync.model.DirectorySnapshot;
import sync.util.AppLogger;

public class SyncServer extends Thread {

    private final int port;
    private DirectorySnapshot localSnapshot;

    public SyncServer(int port) {
        this.port = port;
    }

    public void setLocalSnapshot(DirectorySnapshot snapshot) {
        this.localSnapshot = snapshot;
    }

    @Override
    public void run() {

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            AppLogger.log("Server started on port " + port);

            while (true) {

                Socket socket = serverSocket.accept();

                AppLogger.log("Client connected");

                try {

                    ObjectOutputStream out =
                            new ObjectOutputStream(socket.getOutputStream());

                    out.flush();

                    ObjectInputStream in =
                            new ObjectInputStream(socket.getInputStream());

                    DirectorySnapshot remote =
                            (DirectorySnapshot) in.readObject();

                    AppLogger.log(
                            "Received snapshot (" +
                                    remote.size() +
                                    " files)"
                    );

                    out.writeObject(localSnapshot);

                    out.flush();

                } catch (Exception e) {

                    AppLogger.log(e.getMessage());

                } finally {

                    socket.close();

                }

            }

        } catch (Exception e) {

            AppLogger.log("Server error: " + e.getMessage());

        }

    }

}