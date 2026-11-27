package sync.network;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import sync.model.DirectorySnapshot;
import sync.util.AppLogger;

public class SyncClient {

    public DirectorySnapshot exchangeSnapshot(
            String host,
            int port,
            DirectorySnapshot localSnapshot) {

        try (
                Socket socket = new Socket(host, port);

                ObjectOutputStream out =
                        new ObjectOutputStream(socket.getOutputStream());

                ObjectInputStream in =
                        new ObjectInputStream(socket.getInputStream())
        ) {

            AppLogger.log("Connected to " + host + ":" + port);

            out.writeObject(localSnapshot);
            out.flush();

            AppLogger.log("Snapshot sent.");

            DirectorySnapshot remoteSnapshot =
                    (DirectorySnapshot) in.readObject();

            AppLogger.log(
                    "Snapshot received (" +
                            remoteSnapshot.size() +
                            " files)"
            );

            return remoteSnapshot;

        } catch (Exception e) {

            AppLogger.log("Connection error: " + e.getMessage());

            return null;

        }

    }

}