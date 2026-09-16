package vn.ptit.ltm.server.service;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Objects;

public final class BCryptPasswordHasher implements PasswordHasher {
    private static final int DEFAULT_LOG_ROUNDS = 12;

    private final int logRounds;

    public BCryptPasswordHasher() {
        this(DEFAULT_LOG_ROUNDS);
    }

    BCryptPasswordHasher(int logRounds) {
        if (logRounds < 4 || logRounds > 16) {
            throw new IllegalArgumentException("BCrypt log rounds must be between 4 and 16");
        }
        this.logRounds = logRounds;
    }

    @Override
    public String hash(String password) {
        Objects.requireNonNull(password, "password");
        return BCrypt.hashpw(password, BCrypt.gensalt(logRounds));
    }

    @Override
    public boolean matches(String password, String passwordHash) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(passwordHash, "passwordHash");
        try {
            return BCrypt.checkpw(password, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
