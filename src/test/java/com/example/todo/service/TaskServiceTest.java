// src/test/java/com/example/todo/service/TaskServiceTest.java
package com.example.todo.service;

import com.example.todo.dto.*;
import com.example.todo.entity.AppUser;
import com.example.todo.entity.Task;
import com.example.todo.entity.TaskShare;
import com.example.todo.entity.enums.ShareRole;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import com.example.todo.mapper.TaskMapper;
import com.example.todo.repository.AppUserRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.TaskShareRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskServiceTest {

    TaskRepository taskRepo = mock(TaskRepository.class);
    AppUserRepository userRepo = mock(AppUserRepository.class);
    TaskShareRepository shareRepo = mock(TaskShareRepository.class);
    TaskMapper mapper = mock(TaskMapper.class);

    TaskService svc = new TaskService(taskRepo, userRepo, shareRepo, mapper);

    UUID ownerId = UUID.randomUUID();
    UUID editorId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();

    Task entityOwned() {
        var t = new Task();
        t.setId(taskId);
        t.setTitle("T");
        t.setStatus(TaskStatus.TODO);
        t.setPriority(TaskPriority.MED);
        var u = new AppUser(); u.setId(ownerId); t.setOwner(u);
        t.setVersion(5);
        return t;
    }

    // ---------- listTasks ----------

    @Test
    @DisplayName("listTasks: builds q like-param and returns mapped DTOs (not empty)")
    void listTasks_maps_and_returns() {
        var e = entityOwned();
        when(taskRepo.findAllAccessibleNative(ownerId, null, null, null)).thenReturn(List.of(e));
        var dto = new TaskDto();
        when(mapper.toDto(e)).thenReturn(dto);

        var result1 = svc.listTasks(ownerId, null, null, null);

        assertThat(result1).hasSize(1).containsExactly(dto); // kill EMPTY_RETURNS(mutant on return)
        verify(taskRepo).findAllAccessibleNative(ownerId, null, null, null);
        verify(mapper).toDto(e);

        reset(taskRepo, mapper);
        when(taskRepo.findAllAccessibleNative(eq(ownerId), eq("%bug%"), eq("DONE"), eq("LOW")))
                .thenReturn(List.of(e));
        when(mapper.toDto(e)).thenReturn(dto);

        var result2 = svc.listTasks(ownerId, "bug", TaskStatus.DONE, TaskPriority.LOW);
        assertThat(result2).hasSize(1).containsExactly(dto);
        verify(taskRepo).findAllAccessibleNative(ownerId, "%bug%", "DONE", "LOW");
    }

    // ---------- createTask ----------

    @Test
    @DisplayName("createTask: sets owner, saves entity, returns mapped DTO (non-null)")
    void createTask_sets_owner_and_returns_dto() {
        var tDto = new TaskCreateDto();
        var e = new Task();
        when(mapper.toEntity(tDto)).thenReturn(e);

        var owner = new AppUser(); owner.setId(ownerId);
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));

        when(taskRepo.save(any(Task.class))).thenAnswer(inv -> {
            Task saved = inv.getArgument(0);
            saved.setId(taskId);
            return saved;
        });

        var expectedDto = new TaskDto();
        when(mapper.toDto(any(Task.class))).thenReturn(expectedDto);

        var result = svc.createTask(ownerId, tDto);

        // owner must be set before save
        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(taskRepo).save(cap.capture());
        assertThat(cap.getValue().getOwner()).isEqualTo(owner); // kill removed setOwner

        assertThat(result).isNotNull().isSameAs(expectedDto);   // kill NULL_RETURNS on method
    }

    @Test
    @DisplayName("createTask: owner not found → 404")
    void createTask_owner_not_found() {
        when(mapper.toEntity(any())).thenReturn(new Task());
        when(userRepo.findById(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.createTask(ownerId, new TaskCreateDto()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- getTask ----------

    @Test
    @DisplayName("getTask: returns mapped DTO and 404 when not authorized/not found")
    void getTask_found_and_not_found() {
        var e = entityOwned();
        when(taskRepo.findAuthorizedById(taskId, ownerId)).thenReturn(Optional.of(e));
        var expectedDto = new TaskDto();
        when(mapper.toDto(e)).thenReturn(expectedDto);

        var dto = svc.getTask(taskId, ownerId);
        assertThat(dto).isSameAs(expectedDto); // kill NULL_RETURNS on method

        when(taskRepo.findAuthorizedById(taskId, ownerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.getTask(taskId, ownerId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- patchTask ----------

    @Test
    @DisplayName("patchTask: not owner and not editor → SecurityException")
    void patchTask_not_authorized() {
        var e = entityOwned(); e.getOwner().setId(otherId);
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));
        when(shareRepo.findByTask_IdAndUser_Id(taskId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.patchTask(taskId, ownerId, e.getVersion(), new TaskPatchDto()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("patchTask: editor (not owner) with correct version → updates and saves")
    void patchTask_editor_happy_path() {
        var e = entityOwned(); e.getOwner().setId(otherId); // current user is not owner
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));
        // current user is editor
        var share = new TaskShare(); share.setRole(ShareRole.editor);
        when(shareRepo.findByTask_IdAndUser_Id(taskId, ownerId)).thenReturn(Optional.of(share));
        when(taskRepo.save(e)).thenReturn(e);
        var expectedDto = new TaskDto(); when(mapper.toDto(e)).thenReturn(expectedDto);

        var patch = new TaskPatchDto();
        var dto = svc.patchTask(taskId, ownerId, e.getVersion(), patch);

        verify(mapper).updateFromPatch(patch, e);
        verify(taskRepo).save(e);
        assertThat(dto).isSameAs(expectedDto);

        // covers lambda on .map(...) and editor==true branches
    }

    @Test
    @DisplayName("patchTask: null If-Match version → PreconditionFailed")
    void patchTask_null_version_precondition() {
        var e = entityOwned();
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> svc.patchTask(taskId, ownerId, null, new TaskPatchDto()))
                .isInstanceOf(TaskService.PreconditionFailedException.class);
    }

    @Test
    @DisplayName("patchTask: optimistic lock exception → PreconditionFailed")
    void patchTask_optimistic_lock() {
        var e = entityOwned();
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));
        when(taskRepo.save(e)).thenThrow(new OptimisticLockException("boom"));

        assertThatThrownBy(() -> svc.patchTask(taskId, ownerId, e.getVersion(), new TaskPatchDto()))
                .isInstanceOf(TaskService.PreconditionFailedException.class);
    }

    // ---------- deleteTask ----------

    @Test
    @DisplayName("deleteTask: only owner can delete; not-found → 404")
    void deleteTask_paths() {
        var e = entityOwned();
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));

        svc.deleteTask(taskId, ownerId);
        verify(taskRepo).delete(e);

        e.getOwner().setId(otherId);
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> svc.deleteTask(taskId, ownerId))
                .isInstanceOf(SecurityException.class);

        when(taskRepo.findById(taskId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.deleteTask(taskId, ownerId))
                .isInstanceOf(EntityNotFoundException.class); // cover lambda orElseThrow
    }

    // ---------- shareTask ----------

    @Test
    @DisplayName("shareTask: only owner; creates user if absent; saves TaskShare with all fields")
    void shareTask_owner_and_user_creation_and_fields() {
        var e = entityOwned();
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));

        // user not found -> create
        when(userRepo.findByEmail("x@e.com")).thenReturn(Optional.empty());
        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            // ensure setEmail was called
            assertThat(u.getEmail()).isEqualTo("x@e.com"); // kill removed setEmail mutant
            u.setId(UUID.randomUUID());
            return u;
        });

        svc.shareTask(taskId, ownerId, "x@e.com", ShareRole.editor);

        // capture TaskShare to ensure id/task/user/role are set (kill removed setters)
        ArgumentCaptor<TaskShare> shareCap = ArgumentCaptor.forClass(TaskShare.class);
        verify(shareRepo).save(shareCap.capture());
        TaskShare savedShare = shareCap.getValue();

        assertThat(savedShare.getTask()).isSameAs(e);
        assertThat(savedShare.getUser()).isNotNull();
        assertThat(savedShare.getRole()).isEqualTo(ShareRole.editor);
        assertThat(savedShare.getId()).isNotNull();
        assertThat(savedShare.getId().getTaskId()).isEqualTo(taskId);
        assertThat(savedShare.getId().getUserId()).isEqualTo(savedShare.getUser().getId());

        // not owner -> forbidden
        e.getOwner().setId(otherId);
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> svc.shareTask(taskId, ownerId, "x@e.com", ShareRole.viewer))
                .isInstanceOf(SecurityException.class);

        // not-found task -> 404 (cover lambda orElseThrow)
        when(taskRepo.findById(taskId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.shareTask(taskId, ownerId, "x@e.com", ShareRole.viewer))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- listShares ----------

    @Test
    @DisplayName("listShares: only owner can view; owner==null forbidden; not-found → 404")
    void listShares_paths() {
        var e = entityOwned();
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));

        var s = new TaskShare();
        var target = new AppUser(); target.setEmail("u@e.com");
        s.setUser(target); s.setRole(ShareRole.viewer);
        when(shareRepo.findByTask_Id(taskId)).thenReturn(List.of(s));

        var list = svc.listShares(taskId, ownerId);
        assertThat(list).singleElement()
                .extracting("email", "role")
                .containsExactly("u@e.com", ShareRole.viewer);

        // not owner
        e.getOwner().setId(otherId);
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> svc.listShares(taskId, ownerId))
                .isInstanceOf(SecurityException.class);

        // owner == null
        var e2 = entityOwned();
        e2.setOwner(null);
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e2));
        assertThatThrownBy(() -> svc.listShares(taskId, ownerId))
                .isInstanceOf(SecurityException.class);

        // not-found task -> 404 (cover lambda orElseThrow)
        when(taskRepo.findById(taskId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.listShares(taskId, ownerId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- revokeShare ----------

    @Test
    @DisplayName("revokeShare: only owner; requires existing target; not-found task → 404")
    void revokeShare_paths() {
        var e = entityOwned();
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));

        var target = new AppUser(); target.setId(UUID.randomUUID()); target.setEmail("z@e.com");
        when(userRepo.findByEmail("z@e.com")).thenReturn(Optional.of(target));

        svc.revokeShare(taskId, ownerId, "z@e.com");
        verify(shareRepo).deleteByTask_IdAndUser_Id(taskId, target.getId());

        // not owner
        e.getOwner().setId(otherId);
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> svc.revokeShare(taskId, ownerId, "z@e.com"))
                .isInstanceOf(SecurityException.class);

        // owner ok, target not found
        e.getOwner().setId(ownerId);
        when(taskRepo.findById(taskId)).thenReturn(Optional.of(e));
        when(userRepo.findByEmail("no@e.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.revokeShare(taskId, ownerId, "no@e.com"))
                .isInstanceOf(EntityNotFoundException.class);

        // task not found (cover lambda orElseThrow in revokeShare)
        when(taskRepo.findById(taskId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.revokeShare(taskId, ownerId, "z@e.com"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
