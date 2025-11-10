package com.example.todo.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = null;

        // 1) Authorization: Bearer <jwt>
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            token = auth.substring(7);
        }

        // 2) Cookie: APP_AUTH=<jwt>
        if (token == null && request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("APP_AUTH".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token != null) {
            try {
                JWTClaimsSet claims = jwtService.verifyToken(token);
                String sub = claims.getSubject();
                if (sub == null) throw new RuntimeException("Missing sub claim");
                UUID userId = UUID.fromString(sub);
                String email = claims.getStringClaim("email");
                String name = claims.getStringClaim("name");

                UserPrincipal principal = new UserPrincipal(userId, email, name);
                var authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

}
