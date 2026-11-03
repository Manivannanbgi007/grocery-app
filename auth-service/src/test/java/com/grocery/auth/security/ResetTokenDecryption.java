package com.grocery.auth.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * For tests: what notification-service's ResetTokenCipher does with an encrypted token (the opposite of
 * ResetTokenCipher.encrypt), so the tests here can check that the event is readable with the key.
 */
public final class ResetTokenDecryption {

    private ResetTokenDecryption() {
    }

    public static String decrypt(String encrypted, long userId, String base64Key) throws Exception {
        byte[] ivAndEncrypted = Base64.getUrlDecoder().decode(encrypted.substring(ResetTokenCipher.VERSION.length()));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES"),
                new GCMParameterSpec(128, ivAndEncrypted, 0, 12));
        cipher.updateAAD(Long.toString(userId).getBytes(StandardCharsets.UTF_8));
        byte[] token = cipher.doFinal(Arrays.copyOfRange(ivAndEncrypted, 12, ivAndEncrypted.length));
        return new String(token, StandardCharsets.UTF_8);
    }
}
