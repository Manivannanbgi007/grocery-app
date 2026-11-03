package com.grocery.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a text, as 64 lowercase hex characters: how random tokens are stored (refresh tokens, password-reset codes),
 * so a copy of the database contains no usable token. A fast hash is enough for them (unlike for passwords): the tokens
 * are random, so there is nothing to guess. MySQL's SHA2(text, 256) gives the same value.
 */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Every Java runtime has SHA-256", ex);
        }
    }
}
