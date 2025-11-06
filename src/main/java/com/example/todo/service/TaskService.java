package com.example.todo.service;

import com.example.todo.dto.TaskCreateDto;
import com.example.todo.dto.TaskDto;
import com.example.todo.dto.TaskPatchDto;
import com.example.todo.entity.AppUser;
import com.example.todo.entity.Task;
import com.example.todo.entity.TaskShare;
import com.example.todo.entity.TaskShare.TaskShareId;
import com.example.todo.entity.enums.ShareRole;
import com.example.todo.dto.SharedUserDto;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import com.example.todo.mapper.TaskMapper;
import com.example.todo.repository.AppUserRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.TaskShareRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepo;
    private final AppUserRepository userRepo;
    private final TaskShareRepository shareRepo;
    private final TaskMapper mapper;

    public TaskService(TaskRepository taskRepo, AppUserRepository userRepo, TaskShareRepository shareRepo, TaskMapper mapper) {
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
        this.shareRepo = shareRepo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listTasks(UUID currentUserId, String q, TaskStatus status, TaskPriority priority) {
        String qparam = StringUtils.hasText(q) ? "%" + q + "%" : null;
        List<Task> tasks = taskRepo.findAllAccessibleNative(currentUserId, qparam, status == null ? null : status.name(), priority == null ? null : priority.name());
        return tasks.stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    public TaskDto createTask(UUID ownerId, TaskCreateDto dto) {
        Task entity = mapper.toEntity(dto);
        AppUser owner = userRepo.findById(ownerId).orElseThrow(() -> new EntityNotFoundException("Owner not found"));
        entity.setOwner(owner);
        Task saved = taskRepo.save(entity);
        return mapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public TaskDto getTask(UUID taskId, UUID currentUserId) {
        Task t = taskRepo.findAuthorizedById(taskId, currentUserId).orElseThrow(() -> new EntityNotFoundException("Task not found or access denied"));
        return mapper.toDto(t);
    }

    @Transactional
    public TaskDto patchTask(UUID taskId, UUID currentUserId, Integer ifMatchVersion, TaskPatchDto patch) {
        Task t = taskRepo.findById(taskId).orElseThrow(() -> new EntityNotFoundException("Task not found"));
        boolean owner = t.getOwner() != null && t.getOwner().getId().equals(currentUserId);
        boolean editor = shareRepo.findByTask_IdAndUser_Id(taskId, currentUserId)
                .map(s -> s.getRole() == ShareRole.editor)
                .orElse(false);
        if (!owner && !editor) {
            throw new SecurityException("Not authorized to edit task");
        }
        if (ifMatchVersion == null || !ifMatchVersion.equals(t.getVersion())) {
            throw new PreconditionFailedException("Version mismatch");
        }
        mapper.updateFromPatch(patch, t);
        try {
            Task saved = taskRepo.save(t);
            return mapper.toDto(saved);
        } catch (OptimisticLockException e) {
            throw new PreconditionFailedException("Optimistic lock error");
        }
    }

    @Transactional
    public void deleteTask(UUID taskId, UUID currentUserId) {
        Task t = taskRepo.findById(taskId).orElseThrow(() -> new EntityNotFoundException("Task not found"));
        if (!t.getOwner().getId().equals(currentUserId)) throw new SecurityException("Only owner can delete");
        taskRepo.delete(t);
    }

    @Transactional
    public void shareTask(UUID taskId, UUID ownerId, String userEmail, ShareRole role) {
        Task t = taskRepo.findById(taskId).orElseThrow(() -> new EntityNotFoundException("Task not found"));
        if (!t.getOwner().getId().equals(ownerId)) throw new SecurityException("Only owner can share");
        AppUser target = userRepo.findByEmail(userEmail).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(userEmail);
            return userRepo.save(u);
        });
        TaskShare.TaskShareId id = new TaskShare.TaskShareId(taskId, target.getId());
        TaskShare ts = new TaskShare();
        ts.setId(id);
        ts.setTask(t);
        ts.setUser(target);
        ts.setRole(role);
        shareRepo.save(ts);
    }

    @Transactional(readOnly = true)
    public List<SharedUserDto> listShares(UUID taskId, UUID currentUserId) {
        Task t = taskRepo.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));
        if (t.getOwner() == null || !t.getOwner().getId().equals(currentUserId)) {
            throw new SecurityException("Only owner can view shares");
        }
        return shareRepo.findByTask_Id(taskId).stream()
                .map(s -> new SharedUserDto(s.getUser().getEmail(), s.getRole()))
                .toList();
    }

    @Transactional
    public void revokeShare(UUID taskId, UUID ownerId, String userEmail) {
        Task t = taskRepo.findById(taskId).orElseThrow(() -> new EntityNotFoundException("Task not found"));
        if (!t.getOwner().getId().equals(ownerId)) throw new SecurityException("Only owner can revoke share");
        AppUser target = userRepo.findByEmail(userEmail).orElseThrow(() -> new EntityNotFoundException("User to revoke not found"));
        shareRepo.deleteByTask_IdAndUser_Id(taskId, target.getId());
    }

    public static class PreconditionFailedException extends RuntimeException {
        public PreconditionFailedException(String msg) { super(msg); }
    }
}
