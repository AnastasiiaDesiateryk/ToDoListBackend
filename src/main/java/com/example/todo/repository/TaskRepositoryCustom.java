package com.example.todo.repository;

import com.example.todo.entity.Task;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.UUID;

public interface TaskRepositoryCustom {
    List<Task> findAllAccessible(UUID userId, @Nullable String q, @Nullable TaskStatus status, @Nullable TaskPriority priority);
}
