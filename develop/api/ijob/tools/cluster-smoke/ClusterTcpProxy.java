import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClusterTcpProxy {
    private final int listenPort;
    private final String targetHost;
    private final int targetPort;
    private final Path pauseMarker;
    private final Set<SocketPair> connections = ConcurrentHashMap.newKeySet();

    private ClusterTcpProxy(int listenPort, String targetHost, int targetPort, Path pauseMarker) {
        this.listenPort = listenPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.pauseMarker = pauseMarker;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Expected listenPort targetHost targetPort pauseMarker");
        }
        new ClusterTcpProxy(
                        Integer.parseInt(args[0]),
                        args[1],
                        Integer.parseInt(args[2]),
                        Path.of(args[3]))
                .run();
    }

    private void run() throws Exception {
        try (ServerSocket server = new ServerSocket(listenPort)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> closeAll(server)));
            Thread.ofPlatform().daemon().start(this::monitorPauseMarker);
            while (!server.isClosed()) {
                Socket client = server.accept();
                if (Files.exists(pauseMarker)) {
                    close(client);
                    continue;
                }
                try {
                    Socket upstream = new Socket(targetHost, targetPort);
                    SocketPair pair = new SocketPair(client, upstream);
                    connections.add(pair);
                    Thread.ofVirtual().start(() -> transfer(pair, client, upstream));
                    Thread.ofVirtual().start(() -> transfer(pair, upstream, client));
                } catch (IOException exception) {
                    close(client);
                }
            }
        }
    }

    private void monitorPauseMarker() {
        boolean paused = false;
        while (!Thread.currentThread().isInterrupted()) {
            boolean current = Files.exists(pauseMarker);
            if (current && !paused) {
                connections.forEach(SocketPair::close);
            }
            paused = current;
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void transfer(SocketPair pair, Socket source, Socket destination) {
        try (InputStream input = source.getInputStream();
                OutputStream output = destination.getOutputStream()) {
            input.transferTo(output);
        } catch (IOException ignored) {
            // Fault injection and peer shutdown deliberately break active streams.
        } finally {
            connections.remove(pair);
            pair.close();
        }
    }

    private void closeAll(ServerSocket server) {
        connections.forEach(SocketPair::close);
        try {
            server.close();
        } catch (IOException ignored) {
            // The listener may already be closed.
        }
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing an already failed socket is harmless.
        }
    }

    private record SocketPair(Socket first, Socket second) {
        private void close() {
            ClusterTcpProxy.close(first);
            ClusterTcpProxy.close(second);
        }
    }
}
