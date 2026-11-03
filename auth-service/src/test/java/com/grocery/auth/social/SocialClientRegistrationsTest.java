package com.grocery.auth.social;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static com.grocery.auth.social.TestProviders.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialClientRegistrationsTest {

    private static final SocialLoginProperties.Provider GOOGLE = provider().clientId("google-id").build();
    private static final SocialLoginProperties.Provider TEST = provider().clientId("grocery-local").issuerUri("http://127.0.0.1:8099/test").build();

    @Test
    void onlyProvidersWithAClientIdAreOffered_noneIsFine() {
        assertThat(registrations("http://localhost:5173", Map.of(), "local").all()).isEmpty();
        assertThat(registrations("http://localhost:5173", Map.of("google", provider().clientId("").build()), "local").all()).isEmpty();
        assertThat(registrations("http://localhost:5173", Map.of(), "local").isAvailable()).isFalse();
    }

    @Test
    void socialLoginSwitchedOff_offersNothing_evenWithCredentials() throws Exception {
        // Not even Apple's key is read: switched off, nothing of social login starts
        Map<String, SocialLoginProperties.Provider> all = Map.of("google", GOOGLE, "apple", provider().teamId("TEAM").build());

        SocialClientRegistrations off = registrations(false, "https://shop.example.com", all, "prod");

        assertThat(off.all()).isEmpty();
        assertThat(off.isAvailable()).isFalse();
        assertThat(off.findByRegistrationId("google")).isNull();
    }

    @Test
    void oneProviderCanBeSwitchedOff_andKeepsItsCredentials() {
        SocialClientRegistrations registrations = registrations("https://shop.example.com",
                Map.of("google", GOOGLE, "microsoft", provider().enabled(false).build()), "prod");

        assertThat(registrations.all()).extracting(ClientRegistration::getRegistrationId).containsExactly("google");
        assertThat(registrations.findByRegistrationId("microsoft")).isNull();
        assertThat(registrations.isAvailable()).isTrue();
    }

    @Test
    void theButtonsComeInAFixedOrder() throws Exception {
        Map<String, SocialLoginProperties.Provider> all = Map.of("test", TEST, "apple", TestProviders.apple(TestProviders.newAppleKey()).build(),
                "facebook", provider().build(), "microsoft", provider().build(), "google", GOOGLE);

        assertThat(registrations("http://localhost:5173", all, "local").all()).extracting(ClientRegistration::getRegistrationId)
                .containsExactly("google", "microsoft", "facebook", "apple", "test");
    }

    @Test
    void googleSendsTheBrowserBackToThePublicAddress_andUsesTheIdTokenOnly() {
        ClientRegistration google = registrations("https://shop.example.com/", Map.of("google", GOOGLE), "prod").findByRegistrationId("google");

        assertThat(google.getRedirectUri()).isEqualTo("https://shop.example.com/api/v1/auth/login/oauth2/code/{registrationId}");
        assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(google.getProviderDetails().getIssuerUri()).isEqualTo("https://accounts.google.com");
        assertThat(google.getProviderDetails().getUserInfoEndpoint().getUri()).isNull();   // no extra call: the ID token says who
        assertThat(google.getClientSettings().isRequireProofKey()).isTrue();
    }

    @Test
    void microsoftUsesTheTenantSetting_andHasNoFixedIssuer() {
        ClientRegistration common = registrations("https://shop.example.com", Map.of("microsoft", provider().build()), "prod")
                .findByRegistrationId("microsoft");
        assertThat(common.getProviderDetails().getAuthorizationUri()).isEqualTo("https://login.microsoftonline.com/common/oauth2/v2.0/authorize");
        assertThat(common.getProviderDetails().getTokenUri()).isEqualTo("https://login.microsoftonline.com/common/oauth2/v2.0/token");
        assertThat(common.getProviderDetails().getJwkSetUri()).isEqualTo("https://login.microsoftonline.com/common/discovery/v2.0/keys");
        assertThat(common.getProviderDetails().getIssuerUri()).isNull();   // MicrosoftIssuerValidator checks it
        assertThat(common.getScopes()).contains("openid");

        ClientRegistration consumers = registrations("https://shop.example.com", Map.of("microsoft", provider().tenant("consumers").build()),
                "prod").findByRegistrationId("microsoft");
        assertThat(consumers.getProviderDetails().getTokenUri()).isEqualTo("https://login.microsoftonline.com/consumers/oauth2/v2.0/token");

        assertThatThrownBy(() -> registrations("https://shop.example.com", Map.of("microsoft", provider().tenant("contoso.com").build()), "prod"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MICROSOFT_TENANT");
    }

    @Test
    void facebookIsPlainOAuth_andAsksTheGraphApiWhoLoggedIn() {
        ClientRegistration facebook = registrations("https://shop.example.com", Map.of("facebook", provider().build()), "prod")
                .findByRegistrationId("facebook");

        assertThat(facebook.getScopes()).containsExactlyInAnyOrder("public_profile", "email");   // no openid: no ID token
        assertThat(facebook.getProviderDetails().getAuthorizationUri()).isEqualTo("https://www.facebook.com/v23.0/dialog/oauth");
        assertThat(facebook.getProviderDetails().getTokenUri()).isEqualTo("https://graph.facebook.com/v23.0/oauth/access_token");
        assertThat(facebook.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo("https://graph.facebook.com/v23.0/me?fields=id,name,email");
        assertThat(facebook.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("id");
        assertThat(facebook.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);

        ClientRegistration newer = registrations("https://shop.example.com", Map.of("facebook", provider().graphVersion("v24.0").build()), "prod")
                .findByRegistrationId("facebook");
        assertThat(newer.getProviderDetails().getTokenUri()).isEqualTo("https://graph.facebook.com/v24.0/oauth/access_token");
    }

    @Test
    void appleGetsASignedClientSecret_andPkceCanBeSwitchedOff() throws Exception {
        KeyPair key = TestProviders.newAppleKey();
        SocialClientRegistrations registrations = registrations("https://shop.example.com",
                Map.of("apple", TestProviders.apple(key).pkce(false).build()), "prod");

        ClientRegistration apple = registrations.findByRegistrationId("apple");
        assertThat(apple.getClientSecret()).startsWith("eyJ");   // a JWT (AppleClientSecretTest checks it)
        assertThat(apple.getScopes()).containsExactlyInAnyOrder("openid", "name", "email");
        assertThat(apple.getProviderDetails().getIssuerUri()).isEqualTo("https://appleid.apple.com");
        assertThat(apple.getProviderDetails().getTokenUri()).isEqualTo("https://appleid.apple.com/auth/token");
        assertThat(apple.getClientSettings().isRequireProofKey()).isFalse();
        // The list of buttons never carries a secret
        assertThat(registrations.all()).allSatisfy(registration -> assertThat(registration.getClientSecret()).isEmpty());
    }

    @Test
    void appleWithoutItsKeyStopsTheServiceWithAClearMessage() {
        assertThatThrownBy(() -> registrations("https://shop.example.com", Map.of("apple", provider().teamId("TEAM").keyId("KEY").build()), "prod"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPLE_PRIVATE_KEY");
        assertThatThrownBy(() -> registrations("https://shop.example.com",
                Map.of("apple", provider().teamId("TEAM").keyId("KEY").privateKey("not a key").build()), "prod"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(".p8").hasMessageNotContaining("not a key");
    }

    @Test
    void theTestProviderOnlyWorksLocally() {
        assertThat(registrations("http://localhost:5173", Map.of("test", TEST), "local").findByRegistrationId("test")).isNotNull();

        assertThatThrownBy(() -> registrations("http://localhost:5173", Map.of("test", TEST), "prod"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("local development only");
        assertThatThrownBy(() -> registrations("https://shop.example.com", Map.of("test", TEST), "local"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Social login switched on (SOCIAL_LOGIN_ENABLED=true). */
    private static SocialClientRegistrations registrations(String publicUrl, Map<String, SocialLoginProperties.Provider> providers,
                                                           String profile) {
        return registrations(true, publicUrl, providers, profile);
    }

    private static SocialClientRegistrations registrations(boolean enabled, String publicUrl,
                                                           Map<String, SocialLoginProperties.Provider> providers, String profile) {
        SocialLoginProperties properties = new SocialLoginProperties(enabled, publicUrl, Duration.ofMinutes(10), Duration.ofSeconds(60),
                Duration.ofMinutes(15), providers);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new SocialClientRegistrations(properties, environment, Clock.systemUTC());
    }
}
