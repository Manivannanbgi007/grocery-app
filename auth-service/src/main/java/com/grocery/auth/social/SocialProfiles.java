package com.grocery.auth.social;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns what a provider says about the person into a SocialProfile: the same shape for every provider, so everything
 * after the provider's answer is one flow. The rules for "email verified" are the provider's own (docs/social-login.md):
 * only a verified email can link a login to an existing account.
 */
@Component
@RequiredArgsConstructor
public class SocialProfiles {

    private final JsonMapper jsonMapper;

    /**
     * @param appleUser Apple's form field "user", {"name":{"firstName":"..","lastName":".."}}: sent only at a person's
     *                  first login with Apple (null otherwise, and ignored for the other providers)
     */
    public SocialProfile of(String provider, OAuth2User user, String appleUser) {
        return switch (provider) {
            case "google" -> google((OidcUser) user);
            case "microsoft" -> microsoft((OidcUser) user);
            case "facebook" -> facebook(user);
            case "apple" -> apple((OidcUser) user, appleName(appleUser));
            case "test" -> test((OidcUser) user);
            default -> throw new IllegalArgumentException("Unknown login provider: " + provider);
        };
    }

    /**
     * Google says email_verified = true for every address it checked once. Google itself advises to trust it as proof of
     * ownership only for Gmail addresses and for Google Workspace accounts (the hd claim, "hosted domain"): another
     * address may have changed owner since.
     */
    static SocialProfile google(OidcUser user) {
        String email = user.getEmail();
        boolean verified = Boolean.TRUE.equals(user.getEmailVerified()) && email != null
                && (email.toLowerCase().endsWith("@gmail.com") || user.getClaimAsString("hd") != null);
        return new SocialProfile("google", user.getSubject(), email, verified, user.getFullName());
    }

    /**
     * Microsoft: the person is found by "sub", never by email or preferred_username (an organization can set those to
     * any address, the "nOAuth" takeover). The email counts as verified only with the optional claim xms_edov ("email
     * domain owner verified", switched on in the app registration).
     */
    static SocialProfile microsoft(OidcUser user) {
        boolean verified = isTrue(user.getClaims().get("xms_edov"));
        return new SocialProfile("microsoft", user.getSubject(), user.getEmail(), verified, user.getFullName());
    }

    /**
     * Facebook: the Graph API's id, name and email (/me). Facebook does not say whether it checked the address, and some
     * accounts have none: never verified, so never linked automatically.
     */
    static SocialProfile facebook(OAuth2User user) {
        return new SocialProfile("facebook", user.getName(), user.getAttribute("email"), false, user.getAttribute("name"));
    }

    /**
     * Apple checks the email (also the relay address of "Hide My Email", which never matches another account). The name
     * is not in the ID token: Apple sends it once, at the first login (appleUser).
     */
    static SocialProfile apple(OidcUser user, String name) {
        return new SocialProfile("apple", user.getSubject(), user.getEmail(), isTrue(user.getClaims().get("email_verified")), name);
    }

    /** The local fake provider: email_verified as the tester typed it in (true or "true"). */
    static SocialProfile test(OidcUser user) {
        return new SocialProfile("test", user.getSubject(), user.getEmail(), isTrue(user.getClaims().get("email_verified")),
                user.getClaimAsString("name"));
    }

    /** A claim that should be a boolean but some providers send as text ("true"). */
    static boolean isTrue(Object claim) {
        return Boolean.TRUE.equals(claim) || "true".equalsIgnoreCase(String.valueOf(claim));
    }

    /** "Asha Rao" from Apple's user field, or null (no field, or not the expected JSON). Only fills in the sign-up form. */
    String appleName(String appleUser) {
        if (appleUser == null || appleUser.isBlank()) {
            return null;
        }
        try {
            AppleName name = jsonMapper.readValue(appleUser, AppleUser.class).name();
            if (name == null) {
                return null;
            }
            String fullName = Stream.of(name.firstName(), name.lastName()).filter(Objects::nonNull).map(String::trim)
                    .filter(part -> !part.isEmpty()).collect(Collectors.joining(" "));
            return fullName.isEmpty() ? null : fullName;
        } catch (JacksonException ex) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AppleUser(AppleName name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AppleName(String firstName, String lastName) {
    }
}
