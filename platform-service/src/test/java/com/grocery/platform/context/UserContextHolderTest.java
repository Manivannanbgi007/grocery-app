package com.grocery.platform.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The value saved in created_by / updated_by (see BaseEntity). */
class UserContextHolderTest {

    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void aLoggedInUserIsSavedAsRoleAndId() {
        UserContextHolder.set(new UserContext(12L, "VENDOR", 3L));
        assertThat(UserContextHolder.currentAuditor()).isEqualTo("VENDOR:12");

        UserContextHolder.set(new UserContext(5L, "DELIVERY_PARTNER", null));
        assertThat(UserContextHolder.currentAuditor()).isEqualTo("DELIVERY_PARTNER:5");
    }

    @Test
    void withoutALoggedInUserItIsTheSystem() {
        // A Kafka listener or a scheduled job: no request, so nothing was set
        assertThat(UserContextHolder.currentAuditor()).isEqualTo("SYSTEM");

        // A request without the gateway's user headers (a public endpoint or a webhook)
        UserContextHolder.set(new UserContext(null, null, null));
        assertThat(UserContextHolder.currentAuditor()).isEqualTo("SYSTEM");
    }

    @Test
    void aUserIdWithoutARoleIsStillSaved() {
        UserContextHolder.set(new UserContext(4L, null, null));
        assertThat(UserContextHolder.currentAuditor()).isEqualTo("USER:4");
    }
}
