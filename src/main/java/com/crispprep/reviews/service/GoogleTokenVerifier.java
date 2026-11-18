package com.crispprep.reviews.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Verifies Google ID tokens: signature (Google's public keys), issuer, audience (our client id) and expiry.
 * Kept as a plain field, not a bean, so it never replaces the JwtDecoder that checks our own tokens.
 */
@Service
public class GoogleTokenVerifier {

    private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final NimbusJwtDecoder decoder;

    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        final String expectedAudience = this.clientId;

        OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not issued by Google", null));
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token was not made for this app", null));

        this.decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience));
    }

    public boolean enabled() { return !clientId.isEmpty(); }

    public String clientId() { return clientId; }

    /** Returns the verified token, or throws a JwtException if anything is wrong. */
    public Jwt verify(String idToken) { return decoder.decode(idToken); }
}
