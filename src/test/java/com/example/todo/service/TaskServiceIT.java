// src/test/java/com/example/todo/service/TaskServiceTest.java
package com.example.todo.service;

import com.example.todo.dto.TaskPatchDto;
import com.example.todo.entity.AppUser;
import com.example.todo.entity.Task;
import com.example.todo.entity.TaskShare;
import com.example.todo.entity.enums.ShareRole;
import com.example.todo.mapper.TaskMapper;
import com.example.todo.repository.AppUserRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.TaskShareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceIT {

    @Mock TaskRepository taskRepo;
    @Mock AppUserRepository userRepo;        // не используется в этих кейсах, но нужен для @InjectMocks
    @Mock TaskShareRepository shareRepo;
    @Mock TaskMapper mapper;

    @InjectMocks TaskService service;

    @Test
    void patch_by_viewer_is_forbidden() {
        // arrange
        UUID taskId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        AppUser owner = new AppUser();
        owner.setId(ownerId);

        Task t = new Task();
        t.setId(taskId);
        t.setOwner(owner);
        t.setVersion(3);

        when(taskRepo.findById(taskId)).thenReturn(Optional.of(t));

        // Роль viewer → нет права на изменение
        TaskShare ts = new TaskShare();
        ts.setRole(ShareRole.viewer);
        when(shareRepo.findByTask_IdAndUser_Id(taskId, viewerId))
                .thenReturn(Optional.of(ts));

        // mapper не должен вызываться, но на всякий случай — заглушка
        doNothing().when(mapper).updateFromPatch(any(TaskPatchDto.class), any(Task.class));

        // act + assert
        assertThrows(SecurityException.class, () ->
                service.patchTask(taskId, viewerId, t.getVersion(), new TaskPatchDto()));

        verify(taskRepo, never()).save(any());
    }

    @Test
    void patch_with_wrong_if_match_throws_precondition_failed() {
        // arrange
        UUID taskId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        AppUser owner = new AppUser();
        owner.setId(ownerId);

        Task t = new Task();
        t.setId(taskId);
        t.setOwner(owner);
        t.setVersion(5); // фактическая версия в БД

        when(taskRepo.findById(taskId)).thenReturn(Optional.of(t));
        // Шаринг не важен — владелец сам редактирует
        when(shareRepo.findByTask_IdAndUser_Id(any(), any())).thenReturn(Optional.empty());

        // act + assert
        assertThrows(TaskService.PreconditionFailedException.class, () ->
                service.patchTask(taskId, ownerId, /* If-Match */ 4, new TaskPatchDto()));

        verify(taskRepo, never()).save(any());
        verify(mapper, never()).updateFromPatch(any(), any());
    }
}
