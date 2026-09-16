package vn.ptit.ltm.server.repository;

import java.sql.SQLException;
import java.util.Optional;

public interface UserRepository {
    Optional<UserAccountRecord> findByUsername(String username) throws SQLException;

    long create(String username, String passwordHash, String displayName) throws SQLException;
}
