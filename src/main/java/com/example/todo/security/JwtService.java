package com.example.todo.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final String issuer;
    private final byte[] secret;

    public JwtService(@Value("${jwt.issuer}") String issuer,
                      @Value("${jwt.secret}") String secret) {
        this.issuer = issuer;
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256");
        }

        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issueToken(UUID userId, String email, String displayName) {
        try {
            JWSSigner signer = new MACSigner(secret);
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(userId.toString())
                    .claim("email", email)
                    .claim("name", displayName)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(7 * 24 * 3600)))
                    .build();
            JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to issue JWT", e);
        }
    }

    public JWTClaimsSet verifyToken(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        JWSVerifier verifier = new MACVerifier(secret);
        if (!jwt.verify(verifier)) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new java.util.Date())) {
            throw new IllegalArgumentException("JWT expired");
        }
        if (!issuer.equals(claims.getIssuer())) {
            throw new IllegalArgumentException("Invalid issuer");
        }
        return claims;
    }
}
