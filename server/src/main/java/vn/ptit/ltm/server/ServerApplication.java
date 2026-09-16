package vn.ptit.ltm.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.server.config.DatabaseConfig;
import vn.ptit.ltm.server.config.DatabaseManager;
import vn.ptit.ltm.server.network.AuthMessageHandler;
import vn.ptit.ltm.server.network.CompositeConnectionListener;
import vn.ptit.ltm.server.network.HeartbeatManager;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.network.TcpServer;
import vn.ptit.ltm.server.lobby.LobbyService;
import vn.ptit.ltm.server.repository.JdbcUserRepository;
import vn.ptit.ltm.server.service.AuthService;
import vn.ptit.ltm.server.service.BCryptPasswordHasher;
import vn.ptit.ltm.server.session.SessionConnectionListener;
import vn.ptit.ltm.server.session.SessionManager;
import vn.ptit.ltm.server.session.SessionStateProvider;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerApplication implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerApplication.class);
    private static final int DEFAULT_PORT = 5555;
    private static final int DEFAULT_WORKER_THREADS = 32;

    private final DatabaseManager databaseManager;
    private final SessionManager sessionManager;
    private final HeartbeatManager heartbeatManager;
    private final TcpServer tcpServer;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();

    private ServerApplication(
            DatabaseManager databaseManager,
            SessionManager sessionManager,
            HeartbeatManager heartbeatManager,
            TcpServer tcpServer
    ) {
        this.databaseManager = databaseManager;
        this.sessionManager = sessionManager;
        this.heartbeatManager = heartbeatManager;
        this.tcpServer = tcpServer;
    }

    public static ServerApplication createDefault() {
        DatabaseManager database = DatabaseManager.initialize(DatabaseConfig.fromEnvironment());
        SessionManager sessions = new SessionManager();
        HeartbeatManager heartbeat = new HeartbeatManager();
        try {
            ConnectionRegistry connections = new ConnectionRegistry();
            LobbyService lobby = new LobbyService(sessions, connections);
            sessions.addEventListener(lobby::broadcastOnlinePlayers);
            AuthService authService = new AuthService(
                    new JdbcUserRepository(database.dataSource()),
                    new BCryptPasswordHasher(),
                    sessions
            );
            AuthMessageHandler messageHandler = new AuthMessageHandler(
                    authService,
                    sessions,
                    SessionStateProvider.basic(),
                    heartbeat,
                    lobby
            );
            TcpServer server = new TcpServer(
                    environmentInt("SERVER_PORT", DEFAULT_PORT, 1, 65_535),
                    environmentInt("SERVER_WORKER_THREADS", DEFAULT_WORKER_THREADS, 1, 1_024),
                    messageHandler,
                    new CompositeConnectionListener(
                            connections,
                            heartbeat,
                            new SessionConnectionListener(sessions)
                    )
            );
            return new ServerApplication(database, sessions, heartbeat, server);
        } catch (RuntimeException exception) {
            heartbeat.close();
            sessions.close();
            database.close();
            throw exception;
        }
    }

    public void start() throws IOException {
        tcpServer.start();
        LOGGER.info("Ludo Game Server started on port {}", tcpServer.port());
    }

    public void awaitTermination() throws InterruptedException {
        stopped.await();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        tcpServer.close();
        heartbeatManager.close();
        sessionManager.close();
        databaseManager.close();
        stopped.countDown();
        LOGGER.info("Ludo Game Server stopped");
    }

    public static void main(String[] args) throws Exception {
        ServerApplication application = ServerApplication.createDefault();
        Thread shutdownHook = new Thread(application::close, "server-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try (application) {
            application.start();
            application.awaitTermination();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // The JVM is already shutting down and executing the hook.
            }
        }
    }

    private static int environmentInt(String name, int defaultValue, int minimum, int maximum) {
        String rawValue = System.getenv(name);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(rawValue);
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(
                        name + " must be between " + minimum + " and " + maximum
                );
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a valid integer", exception);
        }
    }
}
