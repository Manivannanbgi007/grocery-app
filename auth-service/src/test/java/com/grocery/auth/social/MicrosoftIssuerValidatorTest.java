package com.grocery.auth.social;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static com.grocery.auth.social.MicrosoftIssuerValidator.PERSONAL_ACCOUNTS;
import static com.grocery.auth.social.TestProviders.provider;
import static org.assertj.core.api.Assertions.assertThat;

/** Microsoft's issuer names the person's own directory: it must match the token's tid, and the tenant setting must allow it. */
class MicrosoftIssuerValidatorTest {

    private static final String CONTOSO = "72f988bf-86f1-41af-91ab-2d7cd011db47";

    @Test
    void commonAcceptsEveryDirectory_whenTheIssuerMatchesTheTenantId() {
        MicrosoftIssuerValidator common = validator(null);

        assertThat(common.validate(token(PERSONAL_ACCOUNTS, issuer(PERSONAL_ACCOUNTS))).hasErrors()).isFalse();
        assertThat(common.validate(token(CONTOSO, issuer(CONTOSO))).hasErrors()).isFalse();
    }

    @Test
    void anIssuerOfAnotherDirectory_orNoTenantId_isRefused() {
        MicrosoftIssuerValidator common = validator("common");

        assertThat(errorOf(common.validate(token(CONTOSO, issuer(PERSONAL_ACCOUNTS))))).contains("does not match");
        assertThat(errorOf(common.validate(token(CONTOSO, "https://evil.example.com/" + CONTOSO + "/v2.0")))).contains("does not match");
        assertThat(errorOf(common.validate(token(null, issuer(CONTOSO))))).contains("no tenant id");
    }

    @Test
    void theTenantSettingLimitsTheDirectories() {
        assertThat(validator("consumers").validate(token(PERSONAL_ACCOUNTS, issuer(PERSONAL_ACCOUNTS))).hasErrors()).isFalse();
        assertThat(validator("consumers").validate(token(CONTOSO, issuer(CONTOSO))).hasErrors()).isTrue();

        assertThat(validator("organizations").validate(token(CONTOSO, issuer(CONTOSO))).hasErrors()).isFalse();
        assertThat(validator("organizations").validate(token(PERSONAL_ACCOUNTS, issuer(PERSONAL_ACCOUNTS))).hasErrors()).isTrue();

        assertThat(validator(CONTOSO.toUpperCase()).validate(token(CONTOSO, issuer(CONTOSO))).hasErrors()).isFalse();
        assertThat(errorOf(validator(CONTOSO).validate(token(PERSONAL_ACCOUNTS, issuer(PERSONAL_ACCOUNTS))))).contains("cannot log in here");
    }

    private static MicrosoftIssuerValidator validator(String tenant) {
        return new MicrosoftIssuerValidator(provider().tenant(tenant).build());
    }

    private static String issuer(String tid) {
        return "https://login.microsoftonline.com/" + tid + "/v2.0";
    }

    private static Jwt token(String tid, String issuer) {
        Jwt.Builder token = Jwt.withTokenValue("id-token").header("alg", "RS256").claim("iss", issuer).claim("sub", "sub-1");
        if (tid != null) {
            token.claim("tid", tid);
        }
        return token.build();
    }

    private static String errorOf(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        return result.getErrors().iterator().next().getDescription();
    }
}
