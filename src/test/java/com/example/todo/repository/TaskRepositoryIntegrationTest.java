// src/test/java/com/example/todo/repository/TaskRepositoryIT.java
package com.example.todo.repository;

import com.example.todo.entity.AppUser;
import com.example.todo.entity.Task;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("todo")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        // если используешь Flyway:
        // r.add("spring.flyway.enabled", () -> true);
    }

    @Autowired AppUserRepository userRepo;
    @Autowired TaskRepository taskRepo;

    AppUser owner;
    AppUser stranger;

    @BeforeEach
    void setUp() {
        taskRepo.deleteAll();
        userRepo.deleteAll();

        owner = new AppUser();
        owner.setEmail("owner@example.com");
        owner = userRepo.save(owner);

        stranger = new AppUser();
        stranger.setEmail("stranger@example.com");
        stranger = userRepo.save(stranger);
    }

    @Test
    void save_and_find_by_authorized_user() {
        Task t = new Task();
        t.setOwner(owner);
        t.setTitle("Test Task");
        t.setDescription("Desc");
        t.setPriority(TaskPriority.MED);   // проверь что такое значение есть в enum
        t.setStatus(TaskStatus.TODO);

        // ✅ tags как List<String>, не массив:
        t.setTags(List.of("tag1", "tag2"));

        // ✅ metadata как JSON-строка (если у тебя setMetadata(String)):
        t.setMetadata("{\"k\":\"v\"}");

        // TODO: если у тебя есть поле деда́йна — поставь верный сеттер:
        // t.setDueAt(OffsetDateTime.now().plusDays(1));

        Task saved = taskRepo.save(t);
        assertThat(saved.getId()).isNotNull();

        var found = taskRepo.findAuthorizedById(saved.getId(), owner.getId());
        assertThat(found).isPresent();

        Task got = found.get();
        assertThat(got.getTitle()).isEqualTo("Test Task");
        assertThat(got.getPriority()).isEqualTo(TaskPriority.MED);
        assertThat(got.getTags()).containsExactly("tag1", "tag2");

        // metadata — строка → проверяем содержимое:
        assertThat(got.getMetadata()).contains("\"k\":\"v\"");
        assertThat(got.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void not_visible_for_other_user() {
        Task t = new Task();
        t.setOwner(owner);
        t.setTitle("Hidden");
        t.setPriority(TaskPriority.MED);
        t.setStatus(TaskStatus.TODO);
        t.setTags(List.of());
        t.setMetadata("{}");
        t = taskRepo.save(t);

        var found = taskRepo.findAuthorizedById(t.getId(), stranger.getId());
        assertThat(found).isNotPresent();
    }
}
