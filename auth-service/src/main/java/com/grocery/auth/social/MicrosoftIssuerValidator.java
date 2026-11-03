package com.grocery.auth.social;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks who issued a Microsoft ID token. Microsoft signs the tokens of every directory (tenant) with the same keys, and
 * the issuer names the person's own directory: https://login.microsoftonline.com/{tid}/v2.0, where tid is a claim of the
 * same token. So the issuer is not one fixed value (the registration has none, and Spring's own issuer check is
 * skipped). Instead: the issuer must match the token's tid, and the tid must be one the "tenant" setting allows.
 *
 *   common         work, school and personal Microsoft accounts (the default)
 *   organizations  work and school accounts only
 *   consumers      personal accounts only (outlook.com, hotmail.com, live.com ...)
 *   a tenant id    one organization only (its "Directory (tenant) ID", a GUID)
 */
public final class MicrosoftIssuerValidator implements OAuth2TokenValidator<Jwt> {

    /** The directory of every personal Microsoft account. */
    static final String PERSONAL_ACCOUNTS = "9188040d-6c67-4c5b-b112-36a304b66dad";
    private static final String MICROSOFT_ISSUER = "https://login.microsoftonline.com/{tid}/v2.0";
    private static final Pattern TENANT_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Set<String> TENANT_GROUPS = Set.of("common", "organizations", "consumers");

    private final String tenant;
    private final String issuerTemplate;

    MicrosoftIssuerValidator(SocialLoginProperties.Provider microsoft) {
        this.tenant = tenantOf(microsoft);
        // Only the tests replace Microsoft's address (with the fake provider's, which has {tid} in it too)
        this.issuerTemplate = microsoft.issuerUri() != null ? microsoft.issuerUri() : MICROSOFT_ISSUER;
    }

    /** The tenant setting (common when it is empty), checked when the service starts. */
    static String tenantOf(SocialLoginProperties.Provider microsoft) {
        String tenant = microsoft.tenant() == null || microsoft.tenant().isBlank() ? "common" : microsoft.tenant().trim().toLowerCase(Locale.ROOT);
        if (!TENANT_GROUPS.contains(tenant) && !TENANT_ID.matcher(tenant).matches()) {
            throw new IllegalStateException("app.social.providers.microsoft.tenant (MICROSOFT_TENANT) must be common, "
                    + "organizations, consumers or a Directory (tenant) ID, not: " + tenant);
        }
        return tenant;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt idToken) {
        String tid = idToken.getClaimAsString("tid");
        if (tid == null || !TENANT_ID.matcher(tid).matches()) {
            return invalid("The Microsoft ID token has no tenant id");
        }
        if (!issuerTemplate.replace("{tid}", tid).equals(idToken.getClaimAsString(JwtClaimNames.ISS))) {
            return invalid("The Microsoft ID token's issuer does not match its tenant id");
        }
        if (!allows(tid)) {
            return invalid("Accounts of this Microsoft directory cannot log in here");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean allows(String tid) {
        return switch (tenant) {
            case "common" -> true;
            case "organizations" -> !PERSONAL_ACCOUNTS.equals(tid);
            case "consumers" -> PERSONAL_ACCOUNTS.equals(tid);
            default -> tenant.equals(tid);
        };
    }

    private static OAuth2TokenValidatorResult invalid(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_id_token", description, null));
    }
}
