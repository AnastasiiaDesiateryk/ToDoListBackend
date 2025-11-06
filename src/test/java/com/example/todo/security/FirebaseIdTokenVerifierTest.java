// src/test/java/com/example/todo/security/FirebaseIdTokenVerifierTest.java
package com.example.todo.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FirebaseIdTokenVerifier}.
 * Uses mocked Nimbus processor — no network access.
 */
@Tag("unit")
class FirebaseIdTokenVerifierTest {

    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private FirebaseIdTokenVerifier verifier;

    @BeforeEach
    void setup() throws Exception {
        jwtProcessor = Mockito.mock(ConfigurableJWTProcessor.class);
        verifier = new FirebaseIdTokenVerifier("demo-project-id");

        // inject mock via reflection (Nimbus RemoteJWKSet is skipped)
        Field field = FirebaseIdTokenVerifier.class.getDeclaredField("jwtProcessor");
        field.setAccessible(true);
        field.set(verifier, jwtProcessor);
    }

    private JWTClaimsSet makeClaims(String issuer, List<String> audience,
                                    String subject, Instant exp, boolean emailVerified) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .expirationTime(Date.from(exp))
                .claim("email", "user@example.com")
                .claim("name", "User")
                .claim("email_verified", emailVerified)
                .build();
    }

    @Test
    @DisplayName("Accepts valid token and returns correct payload")
    void accepts_valid_token() throws Exception {
        var claims = makeClaims(
                "https://securetoken.google.com/demo-project-id",
                List.of("demo-project-id"),
                "uid-123",
                Instant.now().plusSeconds(3600),
                true);
        when(jwtProcessor.process(any(String.class), any())).thenReturn(claims);

        var payload = verifier.verify("token");

        assertThat(payload.email()).isEqualTo("user@example.com");
        assertThat(payload.name()).isEqualTo("User");
        assertThat(payload.emailVerified()).isTrue();
    }

    // src/test/java/com/example/todo/security/FirebaseIdTokenVerifierTest.java
    @Test
    @DisplayName("Rejects token with wrong issuer")
    void rejects_invalid_issuer() throws Exception {
        var claims = makeClaims(
                "https://evil.issuer",
                List.of("demo-project-id"),
                "uid-123",
                Instant.now().plusSeconds(3600),
                true);
        when(jwtProcessor.process(any(String.class), any())).thenReturn(claims);

        assertThatThrownBy(() -> verifier.verify("t"))
                .isInstanceOf(FirebaseIdTokenVerifier.InvalidToken.class)
                .hasMessageContaining("Verification failed")
                .hasRootCauseMessage("Invalid issuer");
    }

    @Test
    @DisplayName("Rejects token with wrong audience")
    void rejects_invalid_audience() throws Exception {
        var claims = makeClaims(
                "https://securetoken.google.com/demo-project-id",
                List.of("other-project"),
                "uid-123",
                Instant.now().plusSeconds(3600),
                true);
        when(jwtProcessor.process(any(String.class), any())).thenReturn(claims);

        assertThatThrownBy(() -> verifier.verify("t"))
                .isInstanceOf(FirebaseIdTokenVerifier.InvalidToken.class)
                .hasRootCauseMessage("Invalid audience");
    }

    @Test
    @DisplayName("Rejects expired token")
    void rejects_expired_token() throws Exception {
        var claims = makeClaims(
                "https://securetoken.google.com/demo-project-id",
                List.of("demo-project-id"),
                "uid-123",
                Instant.now().minusSeconds(10),
                true);
        when(jwtProcessor.process(any(String.class), any())).thenReturn(claims);

        assertThatThrownBy(() -> verifier.verify("t"))
                .isInstanceOf(FirebaseIdTokenVerifier.InvalidToken.class)
                .hasRootCauseMessage("Token expired");
    }

    @Test
    @DisplayName("Rejects token without subject")
    void rejects_blank_subject() throws Exception {
        var claims = makeClaims(
                "https://securetoken.google.com/demo-project-id",
                List.of("demo-project-id"),
                "",
                Instant.now().plusSeconds(3600),
                true);
        when(jwtProcessor.process(any(String.class), any())).thenReturn(claims);

        assertThatThrownBy(() -> verifier.verify("t"))
                .isInstanceOf(FirebaseIdTokenVerifier.InvalidToken.class)
                .hasRootCauseMessage("Empty subject");
    }


    @Test
    @DisplayName("Wraps processor exception in InvalidToken")
    void wraps_low_level_exception() throws Exception {
        when(jwtProcessor.process(any(String.class), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> verifier.verify("t"))
                .isInstanceOf(FirebaseIdTokenVerifier.InvalidToken.class)
                .hasMessageContaining("Verification failed")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
