// src/test/java/com/example/todo/web/UserControllerTest.java
package com.example.todo.web;

import com.example.todo.dto.MeDto;
import com.example.todo.entity.AppUser;
import com.example.todo.security.UserPrincipal;
import com.example.todo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {

    UserService userService = mock(UserService.class);
    MockMvc mvc;

    UserPrincipal principal;
    TestingAuthenticationToken auth;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
        principal = new UserPrincipal(UUID.randomUUID(), "me@example.com", "Me");
        auth = new TestingAuthenticationToken(principal, null, "ROLE_USER");
        auth.setAuthenticated(true);
    }

    @Test
    @DisplayName("GET /api/me returns current user data")
    void me_returns_user() throws Exception {
        var u = new AppUser();
        u.setId(principal.getId());
        u.setEmail("me@example.com");
        u.setDisplayName("Me");
        when(userService.getById(eq(principal.getId()))).thenReturn(u);

        mvc.perform(get("/api/me").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(principal.getId().toString()))
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.displayName").value("Me"));

        verify(userService).getById(principal.getId());
    }
}
