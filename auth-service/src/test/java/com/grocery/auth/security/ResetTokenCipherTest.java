package com.grocery.auth.security;

import com.grocery.auth.config.PasswordResetProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResetTokenCipherTest {

    /** The local default key (application.yml): Base64 of "local-dev-only-change-me-0123456" */
    private static final String KEY = "bG9jYWwtZGV2LW9ubHktY2hhbmdlLW1lLTAxMjM0NTY=";
    private static final String TOKEN = "5f2b9c0e8d7a4e1f9b3c6a2d8e0f4b7c";

    private final ResetTokenCipher cipher = new ResetTokenCipher(new PasswordResetProperties(30, false, KEY));

    @Test
    void encryptsSoThatTheKeyAndTheUserIdGiveTheTokenBack() throws Exception {
        String encrypted = cipher.encrypt(TOKEN, 4L);

        assertThat(encrypted).startsWith("v1.").doesNotContain(TOKEN);
        assertThat(ResetTokenDecryption.decrypt(encrypted, 4L, KEY)).isEqualTo(TOKEN);
    }

    @Test
    void usesANewIvEveryTime_soTheSameTokenNeverLooksTheSame() {
        assertThat(cipher.encrypt(TOKEN, 4L)).isNotEqualTo(cipher.encrypt(TOKEN, 4L));
    }

    @Test
    void doesNotDecryptForAnotherUser_orWithAnotherKey() {
        String encrypted = cipher.encrypt(TOKEN, 4L);
        String otherKey = Base64.getEncoder().encodeToString("another-key-with-32-bytes-012345".getBytes());

        assertThatThrownBy(() -> ResetTokenDecryption.decrypt(encrypted, 5L, KEY)).isInstanceOf(AEADBadTagException.class);
        assertThatThrownBy(() -> ResetTokenDecryption.decrypt(encrypted, 4L, otherKey)).isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void refusesToStartWithAKeyThatIsNot32Bytes() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new ResetTokenCipher(new PasswordResetProperties(30, false, shortKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PASSWORD_RESET_EVENT_KEY");
    }
}
