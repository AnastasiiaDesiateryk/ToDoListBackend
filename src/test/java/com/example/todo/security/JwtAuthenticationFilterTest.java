// src/test/java/com/example/todo/security/JwtAuthenticationFilterTest.java
package com.example.todo.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = Mockito.mock(JwtService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        Mockito.reset(jwtService);
    }

    private static JWTClaimsSet claims(UUID id, String email, String name) {
        return new JWTClaimsSet.Builder()
                .subject(id.toString())
                .claim("email", email)
                .claim("name", name)
                .build();
    }

    @Test
    @DisplayName("Authorization: Bearer <token> → аутентификация проставляется")
    void authenticatesFromAuthorizationHeader() throws Exception {
        String token = "jwt-abc";
        var req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        UUID userId = UUID.randomUUID();
        when(jwtService.verifyToken(token)).thenReturn(claims(userId, "user@example.com", "User Name"));

        filter.doFilter(req, res, chain);

        verify(jwtService).verifyToken(token);
        verify(chain).doFilter(req, res);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getAuthorities())
                .extracting((GrantedAuthority a) -> a.getAuthority())
                .containsExactly("ROLE_USER");
        assertThat(auth.getPrincipal()).isInstanceOf(UserPrincipal.class);

        var principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getEmail()).isEqualTo("user@example.com");
        assertThat(principal.getDisplayName()).isEqualTo("User Name");
    }

    @Test
    @DisplayName("Cookie APP_AUTH=<token> → аутентификация проставляется (fallback)")
    void authenticatesFromCookie() throws Exception {
        String token = "jwt-from-cookie";
        var req = new MockHttpServletRequest();
        req.setCookies(new Cookie("APP_AUTH", token));
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        UUID userId = UUID.randomUUID();
        when(jwtService.verifyToken(token)).thenReturn(claims(userId, "cookie@ex.com", "Cookie User"));

        filter.doFilter(req, res, chain);

        verify(jwtService).verifyToken(token);
        verify(chain).doFilter(req, res);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        var principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getEmail()).isEqualTo("cookie@ex.com");
        assertThat(principal.getDisplayName()).isEqualTo("Cookie User");
    }

    @Test
    @DisplayName("Не Bearer в Authorization (например Basic) → игнор, контекст пуст")
    void ignoresNonBearerAuthorization() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Basic abcdef"); // это не ошибка, инспекция IDE косметическая
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(jwtService, never()).verifyToken(anyString());
        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Нет токена ни в header, ни в cookie → контекст пуст, цепочка продолжается")
    void noTokenLeavesContextEmpty() throws Exception {
        var req = new MockHttpServletRequest();
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("verifyToken бросает исключение → контекст очищен, цепочка продолжается")
    void invalidTokenClearsContext() throws Exception {
        String token = "bad";
        var req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        var res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.verifyToken(token)).thenThrow(new RuntimeException("jwt invalid"));

        // предварительно положим что-то в контекст, чтобы проверить очистку
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("pre", "set")
        );

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Nested
    @DisplayName("Плохие/некорректные claims")
    class BadClaims {

        @Test
        @DisplayName("Отсутствует subject → контекст очищен")
        void missingSubjectClearsContext() throws Exception {
            String token = "no-sub";
            var req = new MockHttpServletRequest();
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            var res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            var claims = new JWTClaimsSet.Builder()
                    .claim("email", "x@y.z")
                    .claim("name", "No Sub")
                    .build();
            when(jwtService.verifyToken(token)).thenReturn(claims);

            filter.doFilter(req, res, chain);

            verify(chain).doFilter(req, res);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("Subject не UUID → контекст очищен")
        void nonUuidSubjectClearsContext() throws Exception {
            String token = "bad-sub";
            var req = new MockHttpServletRequest();
            req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            var res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            var claims = new JWTClaimsSet.Builder()
                    .subject("not-a-uuid")
                    .claim("email", "x@y.z")
                    .claim("name", "Bad Sub")
                    .build();
            when(jwtService.verifyToken(token)).thenReturn(claims);

            filter.doFilter(req, res, chain);

            verify(chain).doFilter(req, res);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
