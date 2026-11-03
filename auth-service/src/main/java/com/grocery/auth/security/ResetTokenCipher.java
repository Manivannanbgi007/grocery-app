package com.grocery.auth.security;

import com.grocery.auth.config.PasswordResetProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the password-reset token for the PasswordResetRequested event. The event travels through the outbox table,
 * Kafka (and a dead-letter topic, if it fails), and all of them keep it for days; encrypted, the token is readable only
 * by notification-service, which has the same key and emails it.
 *
 * AES-256-GCM: it encrypts and also detects any change to the text. Every encryption uses a new random 12-byte IV
 * (never reuse one with the same key). The user id is bound to it as "additional data": the text only decrypts in an
 * event of the same user. The format, "v1." + Base64url(IV + encrypted token + 16-byte tag), leaves room for a new key or
 * algorithm later ("v2.").
 *
 * notification-service's ResetTokenCipher does the opposite; both must stay the same.
 */
@Component
public class ResetTokenCipher {

    static final String VERSION = "v1.";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public ResetTokenCipher(PasswordResetProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.eventKey());
        if (keyBytes.length != KEY_BYTES) {
            // Fails the start: a wrong key would make every reset email unreadable
            throw new IllegalStateException("app.password-reset.event-key (PASSWORD_RESET_EVENT_KEY) must be "
                    + KEY_BYTES + " bytes in Base64, but has " + keyBytes.length + " bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String token, long userId) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(Long.toString(userId).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] ivAndEncrypted = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return VERSION + Base64.getUrlEncoder().withoutPadding().encodeToString(ivAndEncrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Every Java runtime has AES-GCM", ex);
        }
    }
}
