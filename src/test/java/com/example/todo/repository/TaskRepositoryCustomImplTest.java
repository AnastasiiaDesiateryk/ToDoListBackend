// src/test/java/com/example/todo/repository/TaskRepositoryCustomImplIT.java
package com.example.todo.repository;

import com.example.todo.entity.AppUser;
import com.example.todo.entity.Task;
import com.example.todo.entity.TaskShare;
import com.example.todo.entity.enums.ShareRole;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import com.example.todo.repository.impl.TaskRepositoryCustomImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice-тест кастомного репозитория:
 *  - владелец видит свои задачи
 *  - пользователь видит расшаренные задачи
 *  - посторонний не видит
 *  - работают фильтры q / status / priority
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TaskRepositoryCustomImpl.class) // гарантируем регистрацию кастомной имплементации
@Tag("repository")
class TaskRepositoryCustomImplIT {

    @Container
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }

    @Autowired AppUserRepository users;
    @Autowired TaskRepository tasks;
    @Autowired TaskShareRepository shares;
    @Autowired TaskRepositoryCustom repo; // интерфейс; реализация подтянута через @Import

    UUID ownerId;
    UUID viewerId;
    UUID strangerId;

    Task ownerFeature;
    Task ownerBug;
    Task viewerOwn; // на всякий случай — задача, принадлежащая viewer (не должна попасть owner'у)

    @BeforeEach
    void seed() {
        shares.deleteAll();
        tasks.deleteAll();
        users.deleteAll();

        var owner = users.save(newUser("owner@example.com"));
        var viewer = users.save(newUser("viewer@example.com"));
        var stranger = users.save(newUser("stranger@example.com"));

        ownerId = owner.getId();
        viewerId = viewer.getId();
        strangerId = stranger.getId();

        // задачи владельца
        ownerFeature = tasks.save(task(owner, "Feature: search screen", "feat", TaskStatus.TODO, TaskPriority.HIGH));
        ownerBug     = tasks.save(task(owner, "Bug: null pointer",     "bug",  TaskStatus.DONE, TaskPriority.MED));

        // задача, принадлежащая viewer (не должна быть видна owner'у, если не расшарена)
        viewerOwn    = tasks.save(task(viewer, "Viewer personal", "misc", TaskStatus.TODO, TaskPriority.LOW));

        // расшариваем одну задачу владельца пользователю viewer
        shares.save(new TaskShare(ownerBug, viewer, ShareRole.viewer));
    }

    @Test
    @DisplayName("Владелец видит все свои задачи")
    void owner_sees_his_tasks() {
        var list = repo.findAllAccessible(ownerId, null, null, null);
        assertThat(list)
                .extracting(Task::getId)
                .containsExactlyInAnyOrder(ownerFeature.getId(), ownerBug.getId());
    }

    @Test
    @DisplayName("Viewer видит только расшаренные задачи владельца (и не видит чужие нерасшаренные)")
    void viewer_sees_only_shared_tasks() {
        var list = repo.findAllAccessible(viewerId, null, null, null);
        assertThat(list)
                .extracting(Task::getId)
                .containsExactly(ownerBug.getId()) // расшаренная
                .doesNotContain(ownerFeature.getId()) // нерасшаренная
                .doesNotContain(viewerOwn.getId());  // своя собственная задача не входит по контракту кастомного метода
    }

    @Test
    @DisplayName("Посторонний не видит ничего")
    void stranger_sees_nothing() {
        var list = repo.findAllAccessible(strangerId, null, null, null);
        assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("Фильтр q ищет по title/description/category (case-insensitive, contains)")
    void filter_by_q() {
        // "search" матчится по title в ownerFeature
        var list = repo.findAllAccessible(ownerId, "Search", null, null);
        assertThat(list)
                .extracting(Task::getId)
                .containsExactly(ownerFeature.getId());
    }

    @Test
    @DisplayName("Фильтры status и priority вместе срабатывают корректно")
    void filter_by_status_and_priority() {
        var list = repo.findAllAccessible(ownerId, null, TaskStatus.DONE, TaskPriority.MED);
        assertThat(list)
                .extracting(Task::getId)
                .containsExactly(ownerBug.getId());
    }

    // ---------- helpers ----------

    private static AppUser newUser(String email) {
        var u = new AppUser();
        u.setEmail(email);
        return u;
    }

    private static Task task(AppUser owner, String title, String category,
                             TaskStatus status, TaskPriority priority) {
        var t = new Task();
        t.setOwner(owner);
        t.setTitle(title);
        t.setCategory(category);
        t.setStatus(status);
        t.setPriority(priority);
        t.setTags(List.of());      // поле не участвует в критериях — для честности заполним
        t.setMetadata("{}");
        return t;
    }
}
