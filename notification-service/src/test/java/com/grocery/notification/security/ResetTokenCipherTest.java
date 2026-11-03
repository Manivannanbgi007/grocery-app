package com.grocery.notification.security;

import com.grocery.notification.exception.UnreadableResetTokenException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResetTokenCipherTest {

    /** The local default key (application.yml): Base64 of "local-dev-only-change-me-0123456" */
    static final String KEY = "bG9jYWwtZGV2LW9ubHktY2hhbmdlLW1lLTAxMjM0NTY=";
    static final String TOKEN = "5f2b9c0e8d7a4e1f9b3c6a2d8e0f4b7c";
    /** TOKEN for user 4, encrypted with KEY by auth-service's ResetTokenCipher: both services must agree on the format */
    static final String ENCRYPTED_BY_AUTH_SERVICE = "v1.eOLGf4uskBkAJBnIyDRL4Gwx-if7D6nJw9y8b54cNjqZMPDGAx_z0gLJaKDfTDdwWulllWTbwTeOds-x";

    private final ResetTokenCipher cipher = new ResetTokenCipher(KEY);

    @Test
    void decryptsWhatAuthServiceEncrypted() {
        assertThat(cipher.decrypt(ENCRYPTED_BY_AUTH_SERVICE, 4L)).isEqualTo(TOKEN);
    }

    @Test
    void refusesTheTokenOfAnotherUser_aChangedText_orAnotherKey() {
        String changed = ENCRYPTED_BY_AUTH_SERVICE.substring(0, 20) + (ENCRYPTED_BY_AUTH_SERVICE.charAt(20) == 'A' ? 'B' : 'A')
                + ENCRYPTED_BY_AUTH_SERVICE.substring(21);
        ResetTokenCipher otherKey = new ResetTokenCipher(Base64.getEncoder().encodeToString("another-key-with-32-bytes-012345".getBytes()));

        assertThatThrownBy(() -> cipher.decrypt(ENCRYPTED_BY_AUTH_SERVICE, 5L)).isInstanceOf(UnreadableResetTokenException.class);
        assertThatThrownBy(() -> cipher.decrypt(changed, 4L)).isInstanceOf(UnreadableResetTokenException.class);
        assertThatThrownBy(() -> otherKey.decrypt(ENCRYPTED_BY_AUTH_SERVICE, 4L))
                .isInstanceOf(UnreadableResetTokenException.class)
                .hasMessageContaining("PASSWORD_RESET_EVENT_KEY")
                .message().doesNotContain(TOKEN);
    }

    @Test
    void refusesAnUnknownFormat() {
        assertThatThrownBy(() -> cipher.decrypt("v2.abc", 4L)).isInstanceOf(UnreadableResetTokenException.class);
    }

    @Test
    void refusesToStartWithAKeyThatIsNot32Bytes() {
        assertThatThrownBy(() -> new ResetTokenCipher(Base64.getEncoder().encodeToString("too-short".getBytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PASSWORD_RESET_EVENT_KEY");
    }
}
