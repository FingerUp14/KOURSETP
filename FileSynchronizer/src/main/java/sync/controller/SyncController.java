package sync.controller;

import java.io.File;

import sync.model.DirectorySnapshot;
import sync.network.SyncClient;
import sync.network.SyncServer;
import sync.util.FileScanner;

public class SyncController {

    private final SyncServer server;
    private final SyncClient client;

    public SyncController(int localPort) {

        server = new SyncServer(localPort);
        server.start();

        client = new SyncClient();

    }

    public DirectorySnapshot synchronize(
            File folder,
            String ip,
            int remotePort) throws Exception {

        DirectorySnapshot localSnapshot =
                FileScanner.scan(folder);

        server.setLocalSnapshot(localSnapshot);

        return client.exchangeSnapshot(
                ip,
                remotePort,
                localSnapshot
        );

    }

}