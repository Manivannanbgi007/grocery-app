package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMessageCleanerTest {

    @Test
    void removesInvisibleCharactersAndExtraSpaces() {
        // zero-width space, right-to-left override, a bell character and a tab run
        String message = "  How much​ is ‮amul\u0007 butter?\t\t ";

        assertThat(UserMessageCleaner.clean(message)).isEqualTo("How much is amul butter?");
    }

    @Test
    void keepsLineBreaksButNotLongRunsOfEmptyLines() {
        assertThat(UserMessageCleaner.clean("I need:\n- milk\n\n\n\n\n- bread")).isEqualTo("I need:\n- milk\n\n- bread");
    }

    @Test
    void keepsOtherLanguagesAndTheRupeeSign() {
        assertThat(UserMessageCleaner.clean("टमाटर कितने का है? ₹50 से कम")).isEqualTo("टमाटर कितने का है? ₹50 से कम");
    }

    @Test
    void messageWithNothingReadableIsRejected() {
        assertThatThrownBy(() -> UserMessageCleaner.clean("​​ \u0000 "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Please type a message");
    }
}
