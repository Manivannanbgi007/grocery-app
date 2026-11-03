package com.grocery.auth.social;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/** Provider settings and an Apple key for the tests, one field at a time (SocialLoginProperties.Provider has many). */
public final class TestProviders {

    private Boolean enabled;
    private String clientId = "client-id";
    private String clientSecret = "secret";
    private String issuerUri;
    private String tenant;
    private String graphVersion;
    private String teamId;
    private String keyId;
    private String privateKey;
    private Boolean pkce;

    static TestProviders provider() {
        return new TestProviders();
    }

    /** Apple's settings, with a newly made key. */
    static TestProviders apple(KeyPair key) {
        return provider().clientId("com.example.shop.web").teamId("TEAM123456").keyId("KEY1234567").privateKey(pem(key));
    }

    TestProviders enabled(Boolean value) {
        enabled = value;
        return this;
    }

    TestProviders clientId(String value) {
        clientId = value;
        return this;
    }

    TestProviders issuerUri(String value) {
        issuerUri = value;
        return this;
    }

    TestProviders tenant(String value) {
        tenant = value;
        return this;
    }

    TestProviders graphVersion(String value) {
        graphVersion = value;
        return this;
    }

    TestProviders teamId(String value) {
        teamId = value;
        return this;
    }

    TestProviders keyId(String value) {
        keyId = value;
        return this;
    }

    TestProviders privateKey(String value) {
        privateKey = value;
        return this;
    }

    TestProviders pkce(Boolean value) {
        pkce = value;
        return this;
    }

    SocialLoginProperties.Provider build() {
        return new SocialLoginProperties.Provider(enabled, clientId, clientSecret, issuerUri, null, null, null, null, tenant, graphVersion,
                teamId, keyId, privateKey, pkce);
    }

    /** An EC P-256 key, like the one in Apple's .p8 file. */
    public static KeyPair newAppleKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** The private key as Apple's .p8 file has it. */
    public static String pem(KeyPair key) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }
}
