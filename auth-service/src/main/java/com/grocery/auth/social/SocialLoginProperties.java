package com.grocery.auth.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Settings under "app.social" (docs/social-login.md). The client ids and secrets come from environment variables
 * (application.yml). A provider is offered when social login is switched on, the provider has a client id, and it is
 * not switched off itself.
 *
 * @param enabled         the main switch (SOCIAL_LOGIN_ENABLED, off by default): off, no provider is offered, the login
 *                        pages show no social buttons, and the social endpoints answer 404
 * @param publicUrl       the address users open the shop with (http://localhost:5173 locally, https://shop.example.com):
 *                        the providers send the browser back to {publicUrl}/api/v1/auth/login/oauth2/code/{provider},
 *                        and the login ends on {publicUrl}/login/social
 * @param flowLifetime    how long a user may stay on the provider's login page
 * @param codeLifetime    how long the one-time code handed to the browser after the provider's answer works
 * @param signupLifetime  how long the sign-up form (or the password to connect) may take
 * @param providers       per provider id (google, microsoft, facebook, apple, test): its credentials and settings
 */
@Validated
@ConfigurationProperties(prefix = "app.social")
public record SocialLoginProperties(
        boolean enabled,
        @NotBlank String publicUrl,
        @NotNull Duration flowLifetime,
        @NotNull Duration codeLifetime,
        @NotNull Duration signupLifetime,
        Map<String, Provider> providers) {

    public SocialLoginProperties {
        publicUrl = publicUrl == null ? null : publicUrl.replaceAll("/+$", "");
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    /**
     * One provider. The real providers need the client id and secret (Apple: the key instead of a secret), plus the
     * provider's own settings below. The addresses replace the provider's own ones: only the tests set them, to point at
     * the fake provider.
     *
     * @param enabled           false switches this provider off and keeps its credentials (GOOGLE_LOGIN_ENABLED ...;
     *                          on by default)
     * @param clientId          the app's id at the provider (Apple: the Services ID)
     * @param clientSecret      the app's secret (not used by Apple)
     * @param issuerUri         tests only: the issuer (Microsoft: with {tid} where the tenant id goes)
     * @param authorizationUri  tests only
     * @param tokenUri          tests only
     * @param jwkSetUri         tests only
     * @param userInfoUri       tests only (Facebook)
     * @param tenant            Microsoft: common (default), organizations, consumers or a tenant id
     * @param graphVersion      Facebook: the Graph API version (default in SocialClientRegistrations)
     * @param teamId            Apple: the developer account's Team ID
     * @param keyId             Apple: the Key ID of the "Sign in with Apple" key
     * @param privateKey        Apple: the content of that key's .p8 file
     * @param pkce              false switches PKCE off, for a provider that refuses it (on by default)
     */
    public record Provider(
            Boolean enabled,
            String clientId,
            String clientSecret,
            String issuerUri,
            String authorizationUri,
            String tokenUri,
            String jwkSetUri,
            String userInfoUri,
            String tenant,
            String graphVersion,
            String teamId,
            String keyId,
            String privateKey,
            Boolean pkce) {

        /** It has a client id and is not switched off (the main switch is checked by SocialClientRegistrations). */
        public boolean isOffered() {
            return !Boolean.FALSE.equals(enabled) && clientId != null && !clientId.isBlank();
        }
    }
}
