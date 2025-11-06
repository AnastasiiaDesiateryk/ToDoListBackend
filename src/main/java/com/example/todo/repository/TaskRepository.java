package com.example.todo.repository;

import com.example.todo.entity.Task;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, TaskRepositoryCustom {

    @Query("select t from Task t left join com.example.todo.entity.TaskShare s on s.task = t where t.id = :id and (t.owner.id = :userId or s.user.id = :userId)")
    Optional<Task> findAuthorizedById(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query(value = """
        SELECT DISTINCT t.*
        FROM task t
        LEFT JOIN task_share s ON s.task_id = t.id
        WHERE (t.owner_id = :userId OR s.user_id = :userId)
          AND (:status IS NULL OR t.status = CAST(:status AS task_status))
          AND (:priority IS NULL OR t.priority = CAST(:priority AS task_priority))
          AND (
              :q IS NULL
              OR t.title       ILIKE :q
              OR t.description ILIKE :q
              OR t.category    ILIKE :q
              OR EXISTS (
                    SELECT 1
                    FROM task_tags tt
                    WHERE tt.task_id = t.id
                      AND tt.tag ILIKE :q
              )
          )
        ORDER BY t.updated_at DESC
        LIMIT 100
    """, nativeQuery = true)
    List<Task> findAllAccessibleNative(@Param("userId") UUID userId,
                                       @Param("q") String q,
                                       @Param("status") String status,
                                       @Param("priority") String priority);
}
