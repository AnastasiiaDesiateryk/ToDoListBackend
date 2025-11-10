// src/main/java/com/example/todo/security/FirebaseIdTokenVerifier.java
package com.example.todo.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Instant;
import java.util.List;

@Service
public class FirebaseIdTokenVerifier {

    private static final String FIREBASE_JWK_URL =
            "https://www.googleapis.com/robot/v1/metadata/jwk/securetoken@system.gserviceaccount.com";

    private final String projectId;
    private final String expectedIssuer;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public FirebaseIdTokenVerifier(@Value("${firebase.project-id}") String projectId) throws Exception {
        this.projectId = projectId;
        this.expectedIssuer = "https://securetoken.google.com/" + projectId;

        // JWK source з кешем усередині Nimbus
        JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(FIREBASE_JWK_URL));
        var keySelector = new JWSAlgorithmFamilyJWSKeySelector<SecurityContext>(
                JWSAlgorithm.Family.RSA, jwkSource);

        this.jwtProcessor = new DefaultJWTProcessor<>();
        this.jwtProcessor.setJWSKeySelector(keySelector);
        // claims перевіримо вручну нижче (issuer/audience/час)
    }

    public Payload verify(String idToken) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(idToken, null);

            // базові перевірки Firebase ID token
            if (!expectedIssuer.equals(claims.getIssuer())) {
                throw new InvalidToken("Invalid issuer");
            }
            List<String> aud = claims.getAudience();
            if (aud == null || !aud.contains(projectId)) {
                throw new InvalidToken("Invalid audience");
            }
            if (claims.getSubject() == null || claims.getSubject().isBlank()) {
                throw new InvalidToken("Empty subject");
            }
            var exp = claims.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now())) {
                throw new InvalidToken("Token expired");
            }

            String email = claims.getStringClaim("email");
            String name = claims.getStringClaim("name");
            Boolean emailVerified = claims.getBooleanClaim("email_verified");

            return new Payload(
                    email,
                    name != null ? name : "",
                    emailVerified != null ? emailVerified : false
            );

        } catch (Exception e) {
            throw new InvalidToken("Verification failed", e);
        }
    }

    public record Payload(String email, String name, boolean emailVerified) {}

    public static class InvalidToken extends RuntimeException {
        public InvalidToken(String m) { super(m); }
        public InvalidToken(String m, Throwable t) { super(m, t); }
    }
}
