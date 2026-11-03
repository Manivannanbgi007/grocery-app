package com.grocery.auth.social;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Facebook's "who logged in" request carries the access token and its appsecret_proof. */
class FacebookAppSecretProofTest {

    private final FacebookAppSecretProof converter = new FacebookAppSecretProof();

    @Test
    void theGraphApiRequestGetsTheProof() {
        RequestEntity<?> request = converter.convert(new OAuth2UserRequest(registration("facebook"), accessToken("user-token")));

        assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer user-token");
        assertThat(request.getUrl()).hasToString("https://graph.facebook.com/v23.0/me?fields=id,name,email&appsecret_proof="
                + FacebookAppSecretProof.hmacSha256("app-secret", "user-token"));
    }

    @Test
    void otherProvidersAreLeftAlone() {
        RequestEntity<?> request = converter.convert(new OAuth2UserRequest(registration("other"), accessToken("user-token")));

        assertThat(request.getUrl().getQuery()).doesNotContain("appsecret_proof");
    }

    @Test
    void theProofIsHmacSha256InHex() {
        // The well-known HMAC-SHA256 example (as in Wikipedia's HMAC article)
        assertThat(FacebookAppSecretProof.hmacSha256("key", "The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    private static ClientRegistration registration(String id) {
        return ClientRegistration.withRegistrationId(id)
                .clientId("app-id")
                .clientSecret("app-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://shop.example.com/api/v1/auth/login/oauth2/code/" + id)
                .authorizationUri("https://www.facebook.com/v23.0/dialog/oauth")
                .tokenUri("https://graph.facebook.com/v23.0/oauth/access_token")
                .userInfoUri("https://graph.facebook.com/v23.0/me?fields=id,name,email")
                .userNameAttributeName("id")
                .build();
    }

    private static OAuth2AccessToken accessToken(String value) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, Instant.now(), Instant.now().plusSeconds(60));
    }
}
