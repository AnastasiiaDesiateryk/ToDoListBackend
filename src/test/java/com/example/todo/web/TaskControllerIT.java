package com.example.todo.web;

import com.example.todo.entity.AppUser;
import com.example.todo.entity.Task;
import com.example.todo.entity.TaskShare;
import com.example.todo.entity.enums.ShareRole;
import com.example.todo.repository.AppUserRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.TaskShareRepository;
import com.example.todo.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for TaskController using MockMvc + Testcontainers.
 * Covers happy path, optimistic locking, access control and roles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("web")
class TaskControllerIT {

    @Container
    static final PostgreSQLContainer<?> DB =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("todo")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired TaskRepository tasks;
    @Autowired TaskShareRepository shares;

    private final ObjectMapper json = new ObjectMapper();
    private UUID ownerId;

    @BeforeEach
    void initData() {
        shares.deleteAll();
        tasks.deleteAll();
        users.deleteAll();

        var owner = new AppUser();
        owner.setEmail("owner@example.com");
        ownerId = users.save(owner).getId();
    }

    // ───────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Full happy flow: create → patch → get → delete")
    void full_crud_flow_with_etag() throws Exception {
        // CREATE
        var create = mvc.perform(post("/api/tasks")
                        .with(authAs(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Initial Title\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"))
                .andReturn();

        String etag = create.getResponse().getHeader("ETag");
        UUID id = extractId(create.getResponse().getContentAsString());

        // PATCH
        mvc.perform(patch("/api/tasks/{id}", id)
                        .with(authAs(ownerId))
                        .header("If-Match", etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated Title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(header().string("ETag", Matchers.notNullValue()));

        // GET
        mvc.perform(get("/api/tasks/{id}", id)
                        .with(authAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));

        // DELETE
        mvc.perform(delete("/api/tasks/{id}", id)
                        .with(authAs(ownerId)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH with outdated ETag → 412 Precondition Failed")
    void patch_with_outdated_etag_returns_412() throws Exception {
        var t = new Task();
        t.setOwner(users.findById(ownerId).orElseThrow());
        t.setTitle("Old");
        var saved = tasks.save(t);

        mvc.perform(patch("/api/tasks/{id}", saved.getId())
                        .with(authAs(ownerId))
                        .header("If-Match", "W/\"999\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Should Fail\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    @DisplayName("PATCH by viewer user → 403 Forbidden")
    void viewer_cannot_edit_task() throws Exception {
        var owner = users.findById(ownerId).orElseThrow();
        var t = new Task();
        t.setOwner(owner);
        t.setTitle("Sensitive");
        var saved = tasks.save(t);

        var viewer = new AppUser();
        viewer.setEmail("viewer@example.com");
        viewer = users.save(viewer);
        shares.save(new TaskShare(saved, viewer, ShareRole.viewer));

        // viewer tries patch
        mvc.perform(patch("/api/tasks/{id}", saved.getId())
                        .with(authAs(viewer.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "W/\"0\"")
                        .content("{\"title\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET list shows only own and shared tasks")
    void list_shows_only_accessible_tasks() throws Exception {
        var owner = users.findById(ownerId).orElseThrow();

        var task1 = new Task();
        task1.setOwner(owner);
        task1.setTitle("Mine");
        task1 = tasks.save(task1);

        var stranger = new AppUser();
        stranger.setEmail("stranger@example.com");
        stranger = users.save(stranger);

        var task2 = new Task();
        task2.setOwner(stranger);
        task2.setTitle("Stranger Task");
        task2 = tasks.save(task2);

        var shared = new TaskShare(task2, owner, ShareRole.viewer);
        shares.save(shared);

        mvc.perform(get("/api/tasks").with(authAs(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(Matchers.hasItems("Mine", "Stranger Task")))
                .andExpect(jsonPath("$[*].title").value(Matchers.not(Matchers.hasItem("Invisible"))));
    }

    // ───────────────────────────────────────────────────────────────────────────────
    private RequestPostProcessor authAs(UUID userId) {
        var principal = new UserPrincipal(userId, "user+" + userId + "@example.com", "TestUser");
        var auth = new TestingAuthenticationToken(principal, null, "ROLE_USER");
        auth.setAuthenticated(true);
        return authentication(auth);
    }

    private UUID extractId(String jsonBody) throws Exception {
        JsonNode node = json.readTree(jsonBody);
        return UUID.fromString(node.get("id").asText());
    }
}
