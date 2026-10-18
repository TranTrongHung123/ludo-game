package vn.ptit.ltm.server.room;

import java.security.SecureRandom;

/** Readable public identifiers; transport/session credentials use their own tokens. */
final class PublicCode {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicCode() { }

    static String roomId() {
        return generate(6);
    }

    static String matchId() {
        return generate(12);
    }

    private static String generate(int length) {
        char[] code = new char[length];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
