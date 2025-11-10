// src/test/java/com/example/todo/web/AuthControllerFirebaseTest.java
package com.example.todo.web;

import com.example.todo.entity.AppUser;
import com.example.todo.security.FirebaseIdTokenVerifier;
import com.example.todo.security.JwtService;
import com.example.todo.service.UserService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local") // чтобы cookie были SameSite=Lax и без Secure
class AuthControllerFirebaseTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    FirebaseIdTokenVerifier firebaseVerifier;

    @MockBean
    UserService userService;

    @MockBean
    JwtService jwtService;

    @Test
    void google_success_setsHttpOnlyCookie() throws Exception {
        var payload = new FirebaseIdTokenVerifier.Payload("a@b.com", "User", true);
        when(firebaseVerifier.verify(eq("good-token"))).thenReturn(payload);

        var user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("a@b.com");
        user.setDisplayName("User");
        when(userService.upsertGoogleUser("a@b.com", "User")).thenReturn(user);

        when(jwtService.issueToken(eq(user.getId()), eq("a@b.com"), eq("User")))
                .thenReturn("mock-jwt");

        mvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"good-token\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", Matchers.allOf(
                        Matchers.containsString("APP_AUTH=mock-jwt"),
                        Matchers.containsString("HttpOnly"),
                        Matchers.containsString("Path=/"),
                        Matchers.containsString("Max-Age="),
                        Matchers.containsString("SameSite=Lax"),
                        Matchers.not(Matchers.containsString("Secure"))
                )));
    }

    @Test
    void google_emailNotVerified_returns401() throws Exception {
        var payload = new FirebaseIdTokenVerifier.Payload("a@b.com", "User", false);
        when(firebaseVerifier.verify(eq("unverified-token"))).thenReturn(payload);

        mvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"unverified-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void google_invalidToken_returns401() throws Exception {
        when(firebaseVerifier.verify(eq("bad")))
                .thenThrow(new FirebaseIdTokenVerifier.InvalidToken("bad"));

        mvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_clearsCookie() throws Exception {
        mvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", Matchers.allOf(
                        Matchers.containsString("APP_AUTH="),
                        Matchers.containsString("Max-Age=0"),
                        Matchers.containsString("HttpOnly"),
                        Matchers.containsString("Path=/"),
                        Matchers.containsString("SameSite=Lax"),
                        Matchers.not(Matchers.containsString("Secure"))
                )));
    }
}
