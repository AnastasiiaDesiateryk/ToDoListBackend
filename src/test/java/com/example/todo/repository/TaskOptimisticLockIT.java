package com.example.todo.repository;

import com.example.todo.entity.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
class TaskOptimisticLockIT {

    @Autowired TaskRepository taskRepository;

    @Test
    void concurrentUpdates_throwOptimisticLock() {
        Task t = new Task();
        t.setId(UUID.randomUUID());
        t.setTitle("X");
        t = taskRepository.saveAndFlush(t);

        Task t1 = taskRepository.findById(t.getId()).orElseThrow();
        Task t2 = taskRepository.findById(t.getId()).orElseThrow();

        t1.setTitle("A");
        taskRepository.saveAndFlush(t1);

        t2.setTitle("B");
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> taskRepository.saveAndFlush(t2));
    }
}
