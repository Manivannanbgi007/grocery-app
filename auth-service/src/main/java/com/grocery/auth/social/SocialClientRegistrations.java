package com.grocery.auth.social;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The providers users can log in with: a "client registration" (Spring Security's word) per provider that has a client
 * id in the settings and is not switched off, and none at all while social login is switched off (SOCIAL_LOGIN_ENABLED).
 * Zero is fine: then the shop shows no social buttons.
 *
 * Every provider's addresses are written here, so nothing is downloaded from the provider at start-up. The OpenID
 * Connect providers (Google, Microsoft, Apple) are not asked for a userinfo document: the ID token already says who
 * logged in. Facebook has no ID token: its Graph API is asked once (FacebookAppSecretProof).
 *
 * The provider sends the browser back to {app.social.public-url}/api/v1/auth/login/oauth2/code/{provider}: the address
 * the user sees (through the gateway), registered in the provider's console. It cannot be taken from the request,
 * because behind the gateway that is auth-service's own host.
 */
@Slf4j
@Component
public class SocialClientRegistrations implements ClientRegistrationRepository {

    public static final String CALLBACK_PATH = "/api/v1/auth/login/oauth2/code/";
    /** The Graph API version Facebook is called with, unless app.social.providers.facebook.graph-version says otherwise */
    static final String FACEBOOK_GRAPH_VERSION = "v23.0";
    private static final String[] OPENID_SCOPES = {"openid", "profile", "email"};

    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();
    /** Signs Apple's client secret (null when Apple is not configured) */
    private final AppleClientSecret appleClientSecret;

    public SocialClientRegistrations(SocialLoginProperties properties, Environment environment, Clock clock) {
        if (!properties.enabled()) {
            appleClientSecret = null;
            log.info("Social login is switched off (SOCIAL_LOGIN_ENABLED is not true): no social login buttons");
            return;
        }
        String redirectUri = properties.publicUrl() + CALLBACK_PATH + "{registrationId}";
        Map<String, SocialLoginProperties.Provider> configured = properties.providers();

        // In the order of the buttons
        SocialLoginProperties.Provider google = configured.get("google");
        if (isOffered(google)) {
            registrations.put("google", google(google, redirectUri));
        }
        SocialLoginProperties.Provider microsoft = configured.get("microsoft");
        if (isOffered(microsoft)) {
            registrations.put("microsoft", microsoft(microsoft, redirectUri));
        }
        SocialLoginProperties.Provider facebook = configured.get("facebook");
        if (isOffered(facebook)) {
            registrations.put("facebook", facebook(facebook, redirectUri));
        }
        SocialLoginProperties.Provider apple = configured.get("apple");
        appleClientSecret = isOffered(apple) ? new AppleClientSecret(apple, clock) : null;
        if (appleClientSecret != null) {
            registrations.put("apple", apple(apple, redirectUri));
        }
        SocialLoginProperties.Provider test = configured.get("test");
        if (isOffered(test)) {
            refuseTestProviderOutsideLocal(properties, environment);
            registrations.put("test", openIdProvider("test", "Test provider (local only)", test, redirectUri));
        }

        if (registrations.isEmpty()) {
            log.warn("Social login is switched on, but no provider has a client id (GOOGLE_CLIENT_ID, MICROSOFT_CLIENT_ID, "
                    + "FACEBOOK_CLIENT_ID, APPLE_CLIENT_ID): no social login buttons");
        } else {
            log.info("Social login is switched on with: {}", String.join(", ", registrations.keySet()));
        }
    }

    private static boolean isOffered(SocialLoginProperties.Provider provider) {
        return provider != null && provider.isOffered();
    }

    /** False when social login is switched off or no provider is configured: then the social endpoints answer 404. */
    public boolean isAvailable() {
        return !registrations.isEmpty();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        ClientRegistration registration = registrations.get(registrationId);
        if (registration != null && "apple".equals(registrationId)) {
            // Apple's token request is signed in with a current client secret
            return ClientRegistration.withClientRegistration(registration).clientSecret(appleClientSecret.value()).build();
        }
        return registration;
    }

    /** The configured providers, in the order of the buttons. */
    public List<ClientRegistration> all() {
        return List.copyOf(registrations.values());
    }

    private static ClientRegistration google(SocialLoginProperties.Provider p, String redirectUri) {
        ClientRegistration.Builder builder = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(p.clientId())
                .clientSecret(p.clientSecret())
                .redirectUri(redirectUri)
                .scope(OPENID_SCOPES)
                .userInfoUri(null)
                .clientSettings(clientSettings(p));
        // Only the tests change Google's own addresses (to the fake provider)
        if (p.issuerUri() != null) {
            builder.issuerUri(p.issuerUri()).authorizationUri(p.authorizationUri()).tokenUri(p.tokenUri()).jwkSetUri(p.jwkSetUri());
        }
        return builder.build();
    }

    /**
     * Microsoft: personal accounts and work or school accounts, as the tenant setting allows. No issuer here: Microsoft's
     * issuer names the person's own directory, which MicrosoftIssuerValidator checks instead.
     */
    private static ClientRegistration microsoft(SocialLoginProperties.Provider p, String redirectUri) {
        String authority = "https://login.microsoftonline.com/" + MicrosoftIssuerValidator.tenantOf(p);
        return ClientRegistration.withRegistrationId("microsoft")
                .clientName("Microsoft")
                .clientId(p.clientId())
                .clientSecret(p.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope(OPENID_SCOPES)
                .authorizationUri(or(p.authorizationUri(), authority + "/oauth2/v2.0/authorize"))
                .tokenUri(or(p.tokenUri(), authority + "/oauth2/v2.0/token"))
                .jwkSetUri(or(p.jwkSetUri(), authority + "/discovery/v2.0/keys"))
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientSettings(clientSettings(p))
                .build();
    }

    /** Facebook: plain OAuth 2.0, no ID token. Who logged in comes from the Graph API: /me?fields=id,name,email. */
    private static ClientRegistration facebook(SocialLoginProperties.Provider p, String redirectUri) {
        String version = or(p.graphVersion(), FACEBOOK_GRAPH_VERSION);
        String graph = "https://graph.facebook.com/" + version;
        return ClientRegistration.withRegistrationId("facebook")
                .clientName("Facebook")
                .clientId(p.clientId())
                .clientSecret(p.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("public_profile", "email")
                .authorizationUri(or(p.authorizationUri(), "https://www.facebook.com/" + version + "/dialog/oauth"))
                .tokenUri(or(p.tokenUri(), graph + "/oauth/access_token"))
                .userInfoUri(or(p.userInfoUri(), graph + "/me?fields=id,name,email"))
                .userNameAttributeName("id")
                .clientSettings(clientSettings(p))
                .build();
    }

    /**
     * Apple: OpenID Connect with three differences. The client secret is signed by the shop (AppleClientSecret, filled
     * in by findByRegistrationId); the answer comes as a form POST (response_mode=form_post, SocialLoginSecurityConfig);
     * the person's name comes only at their first login, in that form.
     */
    private static ClientRegistration apple(SocialLoginProperties.Provider p, String redirectUri) {
        return ClientRegistration.withRegistrationId("apple")
                .clientName("Apple")
                .clientId(p.clientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "name", "email")
                .issuerUri(or(p.issuerUri(), "https://appleid.apple.com"))
                .authorizationUri(or(p.authorizationUri(), "https://appleid.apple.com/auth/authorize"))
                .tokenUri(or(p.tokenUri(), "https://appleid.apple.com/auth/token"))
                .jwkSetUri(or(p.jwkSetUri(), "https://appleid.apple.com/auth/keys"))
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientSettings(clientSettings(p))
                .build();
    }

    /** An OpenID Connect provider described by its issuer address alone (the fake provider: /authorize, /token, /jwks). */
    private static ClientRegistration openIdProvider(String id, String name, SocialLoginProperties.Provider p, String redirectUri) {
        String issuer = p.issuerUri().replaceAll("/+$", "");
        return ClientRegistration.withRegistrationId(id)
                .clientName(name)
                .clientId(p.clientId())
                .clientSecret(p.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope(OPENID_SCOPES)
                .issuerUri(issuer)
                .authorizationUri(issuer + "/authorize")
                .tokenUri(issuer + "/token")
                .jwkSetUri(issuer + "/jwks")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientSettings(clientSettings(p))
                .build();
    }

    /** PKCE is on for every provider (Spring Security's default); pkce: false switches it off for one that refuses it. */
    private static ClientRegistration.ClientSettings clientSettings(SocialLoginProperties.Provider p) {
        return ClientRegistration.ClientSettings.builder().requireProofKey(!Boolean.FALSE.equals(p.pkce())).build();
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * The fake provider lets anyone claim any (verified) email: anywhere else it would hand out every account. So it only
     * works with the local and test profiles, and never on an https shop address.
     */
    private static void refuseTestProviderOutsideLocal(SocialLoginProperties properties, Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("local", "test")) || properties.publicUrl().startsWith("https://")) {
            throw new IllegalStateException("The test login provider (app.social.providers.test) is for local development "
                    + "only. Remove it from this environment's settings.");
        }
    }
}
