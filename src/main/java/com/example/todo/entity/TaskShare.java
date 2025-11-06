package com.example.todo.entity;

import com.example.todo.entity.enums.ShareRole;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_share")
public class TaskShare {

    @EmbeddedId
    private TaskShareId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("taskId")
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "share_role")
    private ShareRole role;

    public TaskShare() {}

    public TaskShare(Task task, AppUser user, ShareRole role) {
        this.id = new TaskShareId(task.getId(), user.getId());
        this.task = task;
        this.user = user;
        this.role = role;
    }

    public TaskShareId getId() { return id; }
    public void setId(TaskShareId id) { this.id = id; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public ShareRole getRole() { return role; }
    public void setRole(ShareRole role) { this.role = role; }

    // --- Embedded ID ---
    @Embeddable
    public static class TaskShareId implements Serializable {

        @Column(name = "task_id", columnDefinition = "uuid", nullable = false)
        private UUID taskId;

        @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
        private UUID userId;

        public TaskShareId() {}

        public TaskShareId(UUID taskId, UUID userId) {
            this.taskId = taskId;
            this.userId = userId;
        }

        public UUID getTaskId() { return taskId; }
        public void setTaskId(UUID taskId) { this.taskId = taskId; }

        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TaskShareId that)) return false;
            return Objects.equals(taskId, that.taskId) &&
                    Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, userId);
        }
    }
}
