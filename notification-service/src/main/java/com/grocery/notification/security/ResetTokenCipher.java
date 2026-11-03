package com.grocery.notification.security;

import com.grocery.notification.exception.UnreadableResetTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Reads the password-reset token of a PasswordResetRequested event. auth-service encrypts it (its ResetTokenCipher), so
 * the outbox table, Kafka and the dead-letter topic never hold it in plain text; this service has the same key
 * (PASSWORD_RESET_EVENT_KEY) and decrypts it only to put it into the email.
 *
 * AES-256-GCM. Format: "v1." + Base64url(12-byte IV + encrypted token + 16-byte tag); the user id is bound as
 * "additional data", so the text decrypts only in an event of the same user. Both classes must stay the same.
 */
@Component
public class ResetTokenCipher {

    private static final String VERSION = "v1.";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;

    public ResetTokenCipher(@Value("${app.password-reset.event-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != KEY_BYTES) {
            // Fails the start: with a wrong key no reset email could be sent
            throw new IllegalStateException("app.password-reset.event-key (PASSWORD_RESET_EVENT_KEY) must be "
                    + KEY_BYTES + " bytes in Base64, but has " + keyBytes.length + " bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** The token. Throws UnreadableResetTokenException for another key, a changed text or another user id. */
    public String decrypt(String encrypted, long userId) {
        if (!encrypted.startsWith(VERSION)) {
            throw new UnreadableResetTokenException("Unknown format of the encrypted reset token (expected " + VERSION + ")");
        }
        try {
            byte[] ivAndEncrypted = Base64.getUrlDecoder().decode(encrypted.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, ivAndEncrypted, 0, IV_BYTES));
            cipher.updateAAD(Long.toString(userId).getBytes(StandardCharsets.UTF_8));
            byte[] token = cipher.doFinal(ivAndEncrypted, IV_BYTES, ivAndEncrypted.length - IV_BYTES);
            return new String(token, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new UnreadableResetTokenException(
                    "The reset token cannot be decrypted: is PASSWORD_RESET_EVENT_KEY the same as auth-service's?", ex);
        }
    }
}
