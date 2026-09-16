package vn.ptit.ltm.server.service;

import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.dto.auth.LoginResult;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.dto.auth.RegisterResult;
import vn.ptit.ltm.common.dto.player.PlayerProfileDto;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.repository.UserRepository;
import vn.ptit.ltm.server.session.PlayerSession;
import vn.ptit.ltm.server.session.SessionManager;

import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public final class AuthService {
    private static final String DUMMY_PASSWORD = "not-a-real-user-password";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionManager sessionManager;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            SessionManager sessionManager
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.dummyPasswordHash = passwordHasher.hash(DUMMY_PASSWORD);
    }

    public RegisterResult register(RegisterRequest request) {
        Objects.requireNonNull(request, "request");
        AuthValidator.validateRegistration(request.username(), request.password(), request.displayName());
        String passwordHash = passwordHasher.hash(request.password());
        try {
            userRepository.create(request.username(), passwordHash, request.displayName());
            UserAccountRecord user = userRepository.findByUsername(request.username())
                    .orElseThrow(() -> new AuthException(
                            ErrorCode.INTERNAL_SERVER_ERROR,
                            "Registered account could not be loaded"
                    ));
            return new RegisterResult(toProfile(user));
        } catch (SQLIntegrityConstraintViolationException exception) {
            throw new AuthException(
                    ErrorCode.USERNAME_ALREADY_EXISTS,
                    "Username already exists",
                    exception
            );
        } catch (SQLException exception) {
            if ("23000".equals(exception.getSQLState())) {
                throw new AuthException(
                        ErrorCode.USERNAME_ALREADY_EXISTS,
                        "Username already exists",
                        exception
                );
            }
            throw new AuthException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Unable to register account",
                    exception
            );
        }
    }

    public LoginResult login(LoginRequest request, String connectionId) {
        Objects.requireNonNull(request, "request");
        AuthValidator.validateLogin(request.username(), request.password());
        try {
            Optional<UserAccountRecord> candidate = userRepository.findByUsername(request.username());
            if (candidate.isEmpty()) {
                passwordHasher.matches(request.password(), dummyPasswordHash);
                throw invalidCredentials();
            }
            UserAccountRecord user = candidate.orElseThrow();
            if (!passwordHasher.matches(request.password(), user.passwordHash())) {
                throw invalidCredentials();
            }
            PlayerSession session = sessionManager.createSession(user, connectionId);
            return new LoginResult(session.sessionId(), toProfile(user));
        } catch (SQLException exception) {
            throw new AuthException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Unable to authenticate account",
                    exception
            );
        }
    }

    private static AuthException invalidCredentials() {
        return new AuthException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
    }

    private static PlayerProfileDto toProfile(UserAccountRecord user) {
        return new PlayerProfileDto(
                Long.toString(user.id()),
                user.username(),
                user.displayName(),
                user.score(),
                user.firstPlaceCount(),
                0,
                0,
                0
        );
    }
}
