package com.example.todo.repository;

import com.example.todo.entity.TaskShare;
import com.example.todo.entity.TaskShare.TaskShareId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskShareRepository extends JpaRepository<TaskShare, TaskShareId> {
    Optional<TaskShare> findByTask_IdAndUser_Id(UUID taskId, UUID userId);
    List<TaskShare> findByTask_Id(UUID taskId);
    void deleteByTask_IdAndUser_Id(UUID taskId, UUID userId);
}
