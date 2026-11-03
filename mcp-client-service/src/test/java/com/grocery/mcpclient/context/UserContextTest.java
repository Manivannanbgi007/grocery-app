package com.grocery.mcpclient.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The role from the gateway decides the audience: the tools and the rules the model gets. */
class UserContextTest {

    @Test
    void everyRoleHasItsAudience_theSuperAdminCountsAsAnAdmin() {
        assertThat(new UserContext(4L, "CUSTOMER", "jwt").audience()).isEqualTo(Audience.CUSTOMER);
        assertThat(new UserContext(3L, "VENDOR", "jwt").audience()).isEqualTo(Audience.VENDOR);
        assertThat(new UserContext(5L, "DELIVERY_PARTNER", "jwt").audience()).isEqualTo(Audience.DELIVERY_PARTNER);
        assertThat(new UserContext(2L, "ADMIN", "jwt").audience()).isEqualTo(Audience.ADMIN);
        assertThat(new UserContext(1L, "SUPER_ADMIN", "jwt").audience()).isEqualTo(Audience.ADMIN);
    }

    @Test
    void anIncompleteLoginOrAnUnknownRoleIsAGuest() {
        assertThat(UserContext.GUEST.audience()).isEqualTo(Audience.GUEST);
        assertThat(new UserContext(3L, "VENDOR", null).audience()).isEqualTo(Audience.GUEST);
        assertThat(new UserContext(9L, "AUDITOR", "jwt").audience()).isEqualTo(Audience.GUEST);
        assertThat(new UserContext(9L, "AUDITOR", "jwt").isLoggedIn()).isFalse();
    }

    @Test
    void onlyThePanelsGetThePanelRules() {
        assertThat(Audience.VENDOR.isPanel()).isTrue();
        assertThat(Audience.DELIVERY_PARTNER.isPanel()).isTrue();
        assertThat(Audience.ADMIN.isPanel()).isTrue();
        assertThat(Audience.CUSTOMER.isPanel()).isFalse();
        assertThat(Audience.GUEST.isPanel()).isFalse();
    }
}
