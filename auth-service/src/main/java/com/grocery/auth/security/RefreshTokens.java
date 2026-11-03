package com.grocery.auth.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Creates refresh tokens and their hashes.
 *
 * A refresh token is 32 random bytes from SecureRandom (256 bits: impossible to guess), written as 43 URL-safe
 * characters. It is NOT a JWT: it means nothing by itself, it only points to a row in user_sessions. The database keeps
 * only its SHA-256 hash (see Sha256), so a copy of the database contains no usable token.
 */
public final class RefreshTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** "x8Fq..." -> 64 hex characters (what is stored in user_sessions.token_hash) */
    public static String hash(String token) {
        return Sha256.hex(token);
    }
}
