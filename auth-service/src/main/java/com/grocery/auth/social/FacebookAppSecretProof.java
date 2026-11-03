package com.grocery.auth.social;

import org.springframework.core.convert.converter.Converter;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequestEntityConverter;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * The request for "who logged in" after a Facebook login: GET /me?fields=id,name,email on the Graph API, with the access
 * token and its appsecret_proof (an HMAC-SHA256 of the token, keyed with the app secret). With "Require App Secret" on
 * in the Facebook app, Facebook refuses API calls without it, so a stolen access token alone is of no use.
 *
 * Only Facebook gets here: the OpenID Connect providers' ID token already says who logged in.
 */
final class FacebookAppSecretProof implements Converter<OAuth2UserRequest, RequestEntity<?>> {

    private final OAuth2UserRequestEntityConverter defaults = new OAuth2UserRequestEntityConverter();

    @Override
    public RequestEntity<?> convert(OAuth2UserRequest userRequest) {
        RequestEntity<?> request = defaults.convert(userRequest);
        ClientRegistration registration = userRequest.getClientRegistration();
        if (request == null || !"facebook".equals(registration.getRegistrationId())) {
            return request;
        }
        String proof = hmacSha256(registration.getClientSecret(), userRequest.getAccessToken().getTokenValue());
        URI uri = UriComponentsBuilder.fromUri(request.getUrl()).queryParam("appsecret_proof", proof).build(true).toUri();
        return new RequestEntity<>(request.getBody(), request.getHeaders(), request.getMethod(), uri);
    }

    /** Hex, as Facebook expects it. */
    static String hmacSha256(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }
}
