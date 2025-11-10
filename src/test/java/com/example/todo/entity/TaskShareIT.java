// src/test/java/com/example/todo/entity/TaskShareTest.java
package com.example.todo.entity;

import com.example.todo.entity.enums.ShareRole;
import com.example.todo.repository.AppUserRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.TaskShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TaskShare} entity and its composite key {@link TaskShare.TaskShareId}.
 * These tests validate JPA mappings, key constraints, enum persistence, and cascade behavior.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("entity")
class TaskShareIT {

    @Container
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }

    @Autowired AppUserRepository users;
    @Autowired TaskRepository tasks;
    @Autowired TaskShareRepository shares;
    @Autowired EntityManager em;

    AppUser owner;
    AppUser viewer;
    Task task;

    @BeforeEach
    void seed() {
        shares.deleteAll();
        tasks.deleteAll();
        users.deleteAll();

        owner = users.save(newUser("owner@example.com"));
        viewer = users.save(newUser("viewer@example.com"));

        task = new Task();
        task.setOwner(owner);
        task.setTitle("Shared doc");
        task = tasks.save(task);
    }

    @Test
    @DisplayName("@MapsId correctly assembles composite PK and reads entity via EmbeddedId")
    void persists_and_reads_by_embedded_id() {
        var share = shares.saveAndFlush(new TaskShare(task, viewer, ShareRole.viewer));

        assertThat(share.getId()).isNotNull();
        assertThat(share.getId().getTaskId()).isEqualTo(task.getId());
        assertThat(share.getId().getUserId()).isEqualTo(viewer.getId());
        assertThat(share.getRole()).isEqualTo(ShareRole.viewer);

        em.clear();
        var loaded = shares.findById(share.getId()).orElseThrow();
        assertThat(loaded.getRole()).isEqualTo(ShareRole.viewer);
        assertThat(loaded.getTask().getId()).isEqualTo(task.getId());
        assertThat(loaded.getUser().getId()).isEqualTo(viewer.getId());
    }

    @Test
    @DisplayName("Duplicate (task,user) pair violates PK constraint")
    void duplicate_key_violates_pk() {
        shares.saveAndFlush(new TaskShare(task, viewer, ShareRole.viewer));
        var duplicate = new TaskShare(task, viewer, ShareRole.editor);

        assertThatThrownBy(() -> shares.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Role update (viewer→editor) persists and reads back correctly")
    void update_role_round_trip() {
        var s = shares.saveAndFlush(new TaskShare(task, viewer, ShareRole.viewer));
        s.setRole(ShareRole.editor);
        shares.saveAndFlush(s);

        em.clear();
        var loaded = shares.findById(s.getId()).orElseThrow();
        assertThat(loaded.getRole()).isEqualTo(ShareRole.editor);
    }

    @Test
    @DisplayName("Deleting TaskShare does not cascade to Task or AppUser")
    void delete_share_does_not_delete_task_or_user() {
        var s = shares.saveAndFlush(new TaskShare(task, viewer, ShareRole.viewer));
        shares.delete(s);
        shares.flush();

        assertThat(shares.findById(s.getId())).isEmpty();
        assertThat(tasks.findById(task.getId())).isPresent();
        assertThat(users.findById(viewer.getId())).isPresent();
        assertThat(users.findById(owner.getId())).isPresent();
    }

    @Test
    @DisplayName("Enum ShareRole is stored as a string ('viewer'/'editor') in the database")
    void enum_persisted_as_string() {
        var s = shares.saveAndFlush(new TaskShare(task, viewer, ShareRole.viewer));

        var roleStr = (String) em.createNativeQuery(
                        "select role from task_share where task_id = ? and user_id = ?")
                .setParameter(1, s.getId().getTaskId())
                .setParameter(2, s.getId().getUserId())
                .getSingleResult();

        assertThat(roleStr).isEqualTo("viewer");
    }

    @Test
    @DisplayName("equals() and hashCode() of EmbeddedId are based on (taskId,userId)")
    void embedded_id_equals_hashcode() {
        var id1 = new TaskShare.TaskShareId(task.getId(), viewer.getId());
        var id2 = new TaskShare.TaskShareId(task.getId(), viewer.getId());
        var id3 = new TaskShare.TaskShareId(task.getId(), owner.getId());

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        assertThat(id1).isNotEqualTo(id3);
    }

    private static AppUser newUser(String email) {
        var u = new AppUser();
        u.setEmail(email);
        return u;
    }
}
