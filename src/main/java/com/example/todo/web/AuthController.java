// src/main/java/com/example/todo/web/AuthController.java
package com.example.todo.web;

import com.example.todo.dto.AuthRequest;
import com.example.todo.security.FirebaseIdTokenVerifier;
import com.example.todo.security.JwtService;
import com.example.todo.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final FirebaseIdTokenVerifier firebaseVerifier;
    private final UserService userService;
    private final JwtService jwtService;
    private final boolean isProd;

    public AuthController(FirebaseIdTokenVerifier firebaseVerifier,
                          UserService userService,
                          JwtService jwtService,
                          @Value("${spring.profiles.active:local}") String profile) {
        this.firebaseVerifier = firebaseVerifier;
        this.userService = userService;
        this.jwtService = jwtService;
        this.isProd = !"local".equalsIgnoreCase(profile);
    }

    // фронт присылает Firebase ID token; мы проверяем и выдаём наш JWT в httpOnly cookie
    @PostMapping("/google")
    public ResponseEntity<Void> google(@Valid @RequestBody AuthRequest request) {
        var p = firebaseVerifier.verify(request.idToken());

        if (!p.emailVerified()) {
            return ResponseEntity.status(401).build();
        }

        var user = userService.upsertGoogleUser(p.email(), p.name());
        String jwt = jwtService.issueToken(user.getId(), user.getEmail(), user.getDisplayName());

        ResponseCookie cookie = ResponseCookie.from("APP_AUTH", jwt)
                .httpOnly(true)
                .secure(isProd)
                .sameSite(isProd ? "None" : "Lax")
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie.from("APP_AUTH", "")
                .httpOnly(true)
                .secure(isProd)
                .sameSite(isProd ? "None" : "Lax")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
