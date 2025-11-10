// src/test/java/com/example/todo/service/UserServiceTest.java
package com.example.todo.service;

import com.example.todo.entity.AppUser;
import com.example.todo.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    AppUserRepository repo = mock(AppUserRepository.class);
    UserService svc = new UserService(repo);

    @Test
    void upsert_existing_same_name_no_save() {
        var u = new AppUser(); u.setEmail("e@e.com"); u.setDisplayName("Name");
        when(repo.findByEmail("e@e.com")).thenReturn(Optional.of(u));

        var res = svc.upsertGoogleUser("e@e.com", "Name");
        assertThat(res).isSameAs(u);
        verify(repo, never()).save(any());
    }

    @Test
    void upsert_existing_new_name_updates_and_saves() {
        var u = new AppUser(); u.setEmail("e@e.com"); u.setDisplayName("Old");
        when(repo.findByEmail("e@e.com")).thenReturn(Optional.of(u));

        var res = svc.upsertGoogleUser("e@e.com", "New");
        assertThat(res.getDisplayName()).isEqualTo("New");
        verify(repo).save(u);
    }

    @Test
    void upsert_new_user_creates_and_saves() {
        when(repo.findByEmail("n@e.com")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = svc.upsertGoogleUser("n@e.com", "Fresh");
        assertThat(res.getEmail()).isEqualTo("n@e.com");
        assertThat(res.getDisplayName()).isEqualTo("Fresh");
        verify(repo).save(any(AppUser.class));
    }

    @Test
    void findByEmail_delegates() {
        svc.findByEmail("x@y"); verify(repo).findByEmail("x@y");
    }

    @Test
    void getById_found_vs_not_found() {
        var id = UUID.randomUUID();
        var u = new AppUser(); u.setId(id);
        when(repo.findById(id)).thenReturn(Optional.of(u));
        assertThat(svc.getById(id)).isSameAs(u);

        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.getById(id)).isInstanceOf(EntityNotFoundException.class);
    }
}
