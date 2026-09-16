package vn.ptit.ltm.server.service;

public interface PasswordHasher {
    String hash(String password);

    boolean matches(String password, String passwordHash);
}
