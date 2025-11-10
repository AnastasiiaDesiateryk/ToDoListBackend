// src/test/java/com/example/todo/web/TaskControllerTest.java
package com.example.todo.web;

import com.example.todo.dto.*;
import com.example.todo.entity.enums.ShareRole;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import com.example.todo.security.UserPrincipal;
import com.example.todo.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskControllerTest {

    TaskService taskService = mock(TaskService.class);
    MockMvc mvc;
    ObjectMapper om = new ObjectMapper();

    UserPrincipal principal;
    TestingAuthenticationToken auth;

    @BeforeEach
    void setup() {
        var controller = new TaskController(taskService);

        // Spring validator
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();

        principal = new UserPrincipal(UUID.randomUUID(), "user@example.com", "User");
        auth = new TestingAuthenticationToken(principal, null, "ROLE_USER");
        auth.setAuthenticated(true);
    }

    @Test
    @DisplayName("GET /api/tasks returns list using query params (q/status/priority)")
    void list_tasks() throws Exception {
        var dto = new TaskDto();
        dto.id = UUID.randomUUID();
        dto.version = 7;
        when(taskService.listTasks(eq(principal.getId()), eq("search"), eq(TaskStatus.DONE), eq(TaskPriority.HIGH)))
                .thenReturn(List.of(dto));

        mvc.perform(get("/api/tasks")
                        .param("q", "search")
                        .param("status", "DONE")
                        .param("priority", "HIGH")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(dto.id.toString()));

        verify(taskService).listTasks(principal.getId(), "search", TaskStatus.DONE, TaskPriority.HIGH);
    }

    @Test
    @DisplayName("POST /api/tasks creates task and returns 201 + Location + ETag")
    void create_task() throws Exception {
        var create = new TaskCreateDto();
        create.title = "New";
        var created = new TaskDto();
        created.id = UUID.randomUUID();
        created.version = 3;

        when(taskService.createTask(eq(principal.getId()), any(TaskCreateDto.class))).thenReturn(created);

        mvc.perform(post("/api/tasks")
                        .contentType("application/json")
                        .content(om.writeValueAsBytes(create))
                        .principal(auth))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/tasks/" + created.id))
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"3\""))
                .andExpect(jsonPath("$.id").value(created.id.toString()));

        ArgumentCaptor<TaskCreateDto> cap = ArgumentCaptor.forClass(TaskCreateDto.class);
        verify(taskService).createTask(eq(principal.getId()), cap.capture());
        assertThat(cap.getValue().title).isEqualTo("New");
    }

    @Test
    @DisplayName("GET /api/tasks/{id} returns task with ETag header")
    void get_task() throws Exception {
        var t = new TaskDto();
        t.id = UUID.randomUUID();
        t.version = 9;
        when(taskService.getTask(t.id, principal.getId())).thenReturn(t);

        mvc.perform(get("/api/tasks/{id}", t.id).principal(auth))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"9\""))
                .andExpect(jsonPath("$.id").value(t.id.toString()));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{id} parses If-Match and returns updated + ETag")
    void patch_task() throws Exception {
        var id = UUID.randomUUID();
        var patch = new TaskPatchDto();
        patch.title = "Up";
        var updated = new TaskDto();
        updated.id = id;
        updated.version = 11;

        when(taskService.patchTask(eq(id), eq(principal.getId()), eq(5), any(TaskPatchDto.class)))
                .thenReturn(updated);

        mvc.perform(patch("/api/tasks/{id}", id)
                        .header("If-Match", "W/\"5\"")
                        .contentType("application/json")
                        .content(om.writeValueAsBytes(patch))
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"11\""))
                .andExpect(jsonPath("$.id").value(id.toString()));

        verify(taskService).patchTask(eq(id), eq(principal.getId()), eq(5), any(TaskPatchDto.class));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{id} without If-Match → 400")
    void patch_task_missing_if_match() throws Exception {
        mvc.perform(patch("/api/tasks/{id}", UUID.randomUUID())
                        .contentType("application/json")
                        .content(om.writeValueAsBytes(new TaskPatchDto()))
                        .principal(auth))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("DELETE /api/tasks/{id} → 204")
    void delete_task() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(delete("/api/tasks/{id}", id).principal(auth))
                .andExpect(status().isNoContent());

        verify(taskService).deleteTask(id, principal.getId());
    }

    @Test
    @DisplayName("GET /api/tasks/{id}/share returns list")
    void list_shares() throws Exception {
        var s = new SharedUserDto("a@b.com", ShareRole.viewer);
        when(taskService.listShares(any(), any())).thenReturn(List.of(s));

        var id = UUID.randomUUID();
        mvc.perform(get("/api/tasks/{id}/share", id).principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("a@b.com"))
                .andExpect(jsonPath("$[0].role").value("viewer"));

        verify(taskService).listShares(id, principal.getId());
    }

    @Test
    @DisplayName("POST /api/tasks/{id}/share with valid body → 204 and delegates to service")
    void share_valid() throws Exception {
        var id = UUID.randomUUID();
        var req = new ShareRequestDto();
        req.userEmail = "x@y.com";
        req.role = ShareRole.editor;

        mvc.perform(post("/api/tasks/{id}/share", id)
                        .contentType("application/json")
                        .content(om.writeValueAsBytes(req))
                        .principal(auth))
                .andExpect(status().isNoContent());

        verify(taskService).shareTask(id, principal.getId(), "x@y.com", ShareRole.editor);
    }

    @Test
    @DisplayName("POST /api/tasks/{id}/share with invalid email → 400")
    void share_invalid_email() throws Exception {
        var id = UUID.randomUUID();
        var req = new ShareRequestDto();
        req.userEmail = "not-an-email"; // violates @Email
        req.role = ShareRole.viewer;

        mvc.perform(post("/api/tasks/{id}/share", id)
                        .contentType("application/json")
                        .content(om.writeValueAsBytes(req))
                        .principal(auth))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    @DisplayName("DELETE /api/tasks/{id}/share?userEmail=... → 204")
    void revoke_share() throws Exception {
        var id = UUID.randomUUID();

        mvc.perform(delete("/api/tasks/{id}/share", id)
                        .param("userEmail", "x@y.com")
                        .principal(auth))
                .andExpect(status().isNoContent());

        verify(taskService).revokeShare(id, principal.getId(), "x@y.com");
    }
}
