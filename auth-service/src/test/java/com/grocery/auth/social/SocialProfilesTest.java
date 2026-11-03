package com.grocery.auth.social;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Who a provider says the person is, and when its email counts as verified (the base of automatic linking). */
class SocialProfilesTest {

    private final SocialProfiles profiles = new SocialProfiles(JsonMapper.builder().build());

    @Test
    void googleGmailAddressVerifiedByGoogle_isVerified() {
        SocialProfile profile = SocialProfiles.google(oidcUser(Map.of("email", "Priya.Sharma@Gmail.com", "email_verified", true,
                "name", "Priya Sharma")));

        assertThat(profile).isEqualTo(new SocialProfile("google", "sub-1", "priya.sharma@gmail.com", true, "Priya Sharma"));
    }

    @Test
    void googleWorkspaceAddress_isVerified_otherAddressesAreNot() {
        assertThat(SocialProfiles.google(oidcUser(Map.of("email", "ravi@freshmart.in", "email_verified", true, "hd", "freshmart.in")))
                .emailVerified()).isTrue();
        // Google checked it once, but an address of another mail provider may have changed owner since
        assertThat(SocialProfiles.google(oidcUser(Map.of("email", "ravi@yahoo.com", "email_verified", true))).emailVerified()).isFalse();
        assertThat(SocialProfiles.google(oidcUser(Map.of("email", "ravi@gmail.com", "email_verified", false))).emailVerified()).isFalse();
    }

    @Test
    void microsoft_isFoundBySub_andItsEmailIsVerifiedOnlyWithXmsEdov() {
        SocialProfile verified = SocialProfiles.microsoft(oidcUser(Map.of("email", "asha@contoso.com", "xms_edov", true,
                "preferred_username", "someone.else@contoso.com", "name", "Asha Rao")));
        assertThat(verified).isEqualTo(new SocialProfile("microsoft", "sub-1", "asha@contoso.com", true, "Asha Rao"));

        // An organization can put any address into email: without xms_edov it is only a suggestion
        assertThat(SocialProfiles.microsoft(oidcUser(Map.of("email", "asha@contoso.com"))).emailVerified()).isFalse();
        assertThat(SocialProfiles.microsoft(oidcUser(Map.of("email", "asha@contoso.com", "xms_edov", "false"))).emailVerified()).isFalse();
    }

    @Test
    void facebook_isFoundByItsId_andNeverVerified() {
        SocialProfile profile = SocialProfiles.facebook(new DefaultOAuth2User(List.of(),
                Map.of("id", "10229", "name", "Farah Book", "email", "farah@example.com"), "id"));
        assertThat(profile).isEqualTo(new SocialProfile("facebook", "10229", "farah@example.com", false, "Farah Book"));

        // A Facebook account made with a phone number has no email
        assertThat(SocialProfiles.facebook(new DefaultOAuth2User(List.of(), Map.of("id", "10230", "name", "Phone Only"), "id")).email())
                .isNull();
    }

    @Test
    void apple_takesTheNameFromTheFirstLoginsForm_andAcceptsTextBooleans() {
        OidcUser apple = oidcUser(Map.of("email", "x7k2@privaterelay.appleid.com", "email_verified", "true", "is_private_email", "true"));

        SocialProfile first = profiles.of("apple", apple, "{\"name\":{\"firstName\":\"Asha\",\"lastName\":\"Rao\"},\"email\":\"x\"}");
        assertThat(first).isEqualTo(new SocialProfile("apple", "sub-1", "x7k2@privaterelay.appleid.com", true, "Asha Rao"));

        // Later logins carry no name; a broken field is ignored
        assertThat(profiles.of("apple", apple, null).fullName()).isNull();
        assertThat(profiles.of("apple", apple, "{not json").fullName()).isNull();
        assertThat(profiles.of("apple", apple, "{\"name\":{\"firstName\":\" Asha \"}}").fullName()).isEqualTo("Asha");
    }

    @Test
    void noEmail_isNeverVerified() {
        SocialProfile profile = SocialProfiles.test(oidcUser(Map.of("email_verified", "true")));

        assertThat(profile.email()).isNull();
        assertThat(profile.emailVerified()).isFalse();
    }

    @Test
    void booleanClaimsMayArriveAsText() {
        assertThat(SocialProfiles.isTrue(true)).isTrue();
        assertThat(SocialProfiles.isTrue("true")).isTrue();
        assertThat(SocialProfiles.isTrue("false")).isFalse();
        assertThat(SocialProfiles.isTrue(null)).isFalse();
    }

    private static OidcUser oidcUser(Map<String, Object> claims) {
        Map<String, Object> all = new HashMap<>(claims);
        all.put("sub", "sub-1");
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60), all);
        return new DefaultOidcUser(List.of(), idToken);
    }
}
