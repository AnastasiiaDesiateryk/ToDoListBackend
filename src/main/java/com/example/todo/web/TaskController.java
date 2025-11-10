package com.example.todo.web;

import com.example.todo.dto.*;
import com.example.todo.entity.enums.ShareRole;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import com.example.todo.security.UserPrincipal;
import com.example.todo.service.TaskService;
import com.example.todo.util.ETagUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@Validated
public class TaskController {

    private final TaskService taskService;
    public TaskController(TaskService taskService) { this.taskService = taskService; }

    @GetMapping
    public ResponseEntity<List<TaskDto>> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) TaskStatus status,
                                              @RequestParam(required = false) TaskPriority priority,
                                              Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(taskService.listTasks(p.getId(), q, status, priority));
    }

    @PostMapping
    public ResponseEntity<TaskDto> create(@Valid @RequestBody TaskCreateDto dto, Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        TaskDto created = taskService.createTask(p.getId(), dto);
        String etag = ETagUtil.formatWeak(created.version);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id))
                .header(HttpHeaders.ETAG, etag)
                .body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> get(@PathVariable UUID id, Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        TaskDto dto = taskService.getTask(id, p.getId());
        return ResponseEntity.ok().header(HttpHeaders.ETAG, ETagUtil.formatWeak(dto.version)).body(dto);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskDto> patch(@PathVariable UUID id,
                                         @RequestHeader(value = "If-Match", required = true) String ifMatch,
                                         @Valid @RequestBody TaskPatchDto patch,
                                         Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        Integer version = ETagUtil.parseIfMatch(ifMatch);
        TaskDto updated = taskService.patchTask(id, p.getId(), version, patch);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, ETagUtil.formatWeak(updated.version)).body(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        taskService.deleteTask(id, p.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/share")
    public ResponseEntity<List<SharedUserDto>> listShares(@PathVariable UUID id, Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(taskService.listShares(id, p.getId()));
    }



    @PostMapping("/{id}/share")
    public ResponseEntity<Void> share(@PathVariable UUID id, @Valid @RequestBody ShareRequestDto req, Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        taskService.shareTask(id, p.getId(), req.userEmail, req.role);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/share")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @RequestParam String userEmail, Authentication auth) {
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        taskService.revokeShare(id, p.getId(), userEmail);
        return ResponseEntity.noContent().build();
    }
}
