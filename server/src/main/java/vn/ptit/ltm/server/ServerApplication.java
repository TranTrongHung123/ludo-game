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
import vn.ptit.ltm.server.repository.JdbcMatchRepository;
import vn.ptit.ltm.server.room.RoomService;
import vn.ptit.ltm.server.service.AuthService;
import vn.ptit.ltm.server.service.BCryptPasswordHasher;
import vn.ptit.ltm.server.service.MatchService;
import vn.ptit.ltm.server.service.RankingService;
import vn.ptit.ltm.server.session.SessionConnectionListener;
import vn.ptit.ltm.server.session.SessionManager;
import vn.ptit.ltm.server.voice.UdpVoiceServer;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerApplication implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerApplication.class);
    private static final int DEFAULT_PORT = 5555;
    private static final int DEFAULT_WORKER_THREADS = 32;
    private static final int DEFAULT_VOICE_UDP_PORT = 5556;

    private final DatabaseManager databaseManager;
    private final SessionManager sessionManager;
    private final HeartbeatManager heartbeatManager;
    private final RoomService roomService;
    private final TcpServer tcpServer;
    private final UdpVoiceServer voiceServer;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();

    private ServerApplication(
            DatabaseManager databaseManager,
            SessionManager sessionManager,
            HeartbeatManager heartbeatManager,
            RoomService roomService,
            TcpServer tcpServer,
            UdpVoiceServer voiceServer
    ) {
        this.databaseManager = databaseManager;
        this.sessionManager = sessionManager;
        this.heartbeatManager = heartbeatManager;
        this.roomService = roomService;
        this.tcpServer = tcpServer;
        this.voiceServer = voiceServer;
    }

    // Khởi tạo database và kết nối các dịch vụ phiên, phòng, heartbeat với TCP Server.
    public static ServerApplication createDefault() {
        DatabaseManager database = DatabaseManager.initialize(DatabaseConfig.fromEnvironment());
        SessionManager sessions = new SessionManager();
        HeartbeatManager heartbeat = new HeartbeatManager();
        RoomService rooms = null;
        UdpVoiceServer voice = null;
        try {
            ConnectionRegistry connections = new ConnectionRegistry();
            LobbyService lobby = new LobbyService(sessions, connections);
            JdbcMatchRepository matches = new JdbcMatchRepository(database.dataSource());
            MatchService matchService = new MatchService(matches, sessions);
            RankingService rankingService = new RankingService(matches);
            rooms = new RoomService(sessions, connections, matchService);
            RoomService activeRooms = rooms;
            voice = new UdpVoiceServer(
                    environmentInt("VOICE_UDP_PORT", DEFAULT_VOICE_UDP_PORT, 1, 65_535),
                    activeRooms::isVoiceParticipant
            );
            UdpVoiceServer activeVoice = voice;
            sessions.addEventListener(lobby::broadcastOnlinePlayers);
            sessions.addEventListener(activeRooms::onSessionsChanged);
            AuthService authService = new AuthService(
                    new JdbcUserRepository(database.dataSource()),
                    new BCryptPasswordHasher(),
                    sessions
            );
            AuthMessageHandler messageHandler = new AuthMessageHandler(
                    authService,
                    sessions,
                    activeRooms::restoreSession,
                    heartbeat,
                    lobby,
                    activeRooms,
                    matchService,
                    rankingService,
                    activeVoice
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
            return new ServerApplication(database, sessions, heartbeat, activeRooms, server, activeVoice);
        } catch (RuntimeException | IOException exception) {
            if (voice != null) {
                voice.close();
            }
            if (rooms != null) {
                rooms.close();
            }
            heartbeat.close();
            sessions.close();
            database.close();
            throw new IllegalStateException("Unable to initialize server", exception);
        }
    }

    // Bắt đầu lắng nghe TCP sau khi các dịch vụ đã khởi tạo.
    public void start() throws IOException {
        voiceServer.start();
        tcpServer.start();
        LOGGER.info("Ludo Game Server started on TCP {} and UDP voice {}", tcpServer.port(), voiceServer.port());
    }

    // Giữ tiến trình chính chờ tới khi Server được đóng.
    public void awaitTermination() throws InterruptedException {
        stopped.await();
    }

    // Dừng các thành phần đúng một lần và giải phóng tài nguyên.
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        tcpServer.close();
        voiceServer.close();
        heartbeatManager.close();
        roomService.close();
        sessionManager.close();
        databaseManager.close();
        stopped.countDown();
        LOGGER.info("Ludo Game Server stopped");
    }

    // Chạy Server và đăng ký dọn dẹp khi tiến trình nhận yêu cầu tắt.
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
                // JVM đang tắt và đã thực thi tác vụ dọn dẹp.
            }
        }
    }

    // Đọc số nguyên cấu hình và chặn giá trị ngoài giới hạn cho phép.
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
