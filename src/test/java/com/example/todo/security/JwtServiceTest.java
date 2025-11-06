// src/test/java/com/example/todo/security/JwtServiceTest.java
package com.example.todo.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String ISS = "issuer-abc";
    private static final String SECRET_OK_32 = "0123456789abcdef0123456789abcdef"; // 32 байта
    private static final String SECRET_OTHER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";  // тоже 32
    private static final String SECRET_SHORT_31 = "0123456789abcdef0123456789abcde"; // 31 (нельзя)

    @Test
    @DisplayName("issue+verify: корректный круг (issuer/email/exp заполнены)")
    void issueAndVerify_ok() throws Exception {
        var svc = new JwtService(ISS, SECRET_OK_32);
        var token = svc.issueToken(UUID.randomUUID(), "user@example.com", "User");
        var claims = svc.verifyToken(token);

        assertEquals(ISS, claims.getIssuer());
        assertEquals("user@example.com", claims.getStringClaim("email"));
        assertNotNull(claims.getExpirationTime());
    }

    @Test
    @DisplayName("verify: неверный issuer → IllegalArgumentException")
    void verify_failsOnWrongIssuer() throws Exception {
        var svc1 = new JwtService("issuer-1", SECRET_OK_32);
        var svc2 = new JwtService("issuer-2", SECRET_OK_32);

        var token = svc1.issueToken(UUID.randomUUID(), "u@e.com", "U");
        assertThrows(IllegalArgumentException.class, () -> svc2.verifyToken(token));
    }

    // ---------- негативные и граничные сценарии ----------

    @Test
    @DisplayName("ctor: secret == null → IllegalArgumentException")
    void constructor_rejects_null_secret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(ISS, null));
    }

    @Test
    @DisplayName("ctor: secret короче 32 символов (31) → IllegalArgumentException")
    void constructor_rejects_short_secret_31() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(ISS, SECRET_SHORT_31));
    }

    @Test
    @DisplayName("ctor: секрет ровно 32 символа допустим (HS256 requirement)")
    void constructor_accepts_boundary_32() {
        assertDoesNotThrow(() -> new JwtService(ISS, SECRET_OK_32));
    }

    @Test
    @DisplayName("verify: плохая подпись (другой ключ) → IllegalArgumentException")
    void verify_failsOnBadSignature() throws Exception {
        var issuerSvc = new JwtService(ISS, SECRET_OTHER);   // подписали другим ключом
        var verifierSvc = new JwtService(ISS, SECRET_OK_32); // проверяем текущим сервисом

        String token = issuerSvc.issueToken(UUID.randomUUID(), "sig@bad.com", "X");
        assertThrows(IllegalArgumentException.class, () -> verifierSvc.verifyToken(token));
    }

    @Test
    @DisplayName("verify: просроченный токен → IllegalArgumentException")
    void verify_failsOnExpired() throws Exception {
        var svc = new JwtService(ISS, SECRET_OK_32);
        String token = buildTokenWith(SECRET_OK_32, b -> b
                .issuer(ISS)
                .subject(UUID.randomUUID().toString())
                .claim("email", "exp@ex.com")
                .claim("name", "Expired")
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(10)))
        );
        assertThrows(IllegalArgumentException.class, () -> svc.verifyToken(token));
    }

    @Test
    @DisplayName("verify: отсутствует exp → IllegalArgumentException")
    void verify_failsOnMissingExpiration() throws Exception {
        var svc = new JwtService(ISS, SECRET_OK_32);
        String token = buildTokenWith(SECRET_OK_32, b -> b
                        .issuer(ISS)
                        .subject(UUID.randomUUID().toString())
                        .claim("email", "noexp@ex.com")
                        .claim("name", "NoExp")
                        .issueTime(new Date())
                // без expirationTime
        );
        assertThrows(IllegalArgumentException.class, () -> svc.verifyToken(token));
    }

    // ---------- helper ----------

    @FunctionalInterface
    interface ClaimsCustomizer { JWTClaimsSet.Builder apply(JWTClaimsSet.Builder b); }

    private static String buildTokenWith(String secret, ClaimsCustomizer customizer) throws Exception {
        var claims = customizer.apply(new JWTClaimsSet.Builder()).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        JWSSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        jwt.sign(signer);
        return jwt.serialize();
    }
}
