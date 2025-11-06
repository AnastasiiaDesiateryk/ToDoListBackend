package com.example.todo.service;

import com.example.todo.entity.AppUser;
import com.example.todo.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final AppUserRepository repo;

    public UserService(AppUserRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public AppUser upsertGoogleUser(String email, String displayName) {
        Optional<AppUser> opt = repo.findByEmail(email);
        if (opt.isPresent()) {
            AppUser u = opt.get();
            if (displayName != null && !displayName.equals(u.getDisplayName())) {
                u.setDisplayName(displayName);
                repo.save(u);
            }
            return u;
        } else {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setDisplayName(displayName);
            AppUser saved = repo.save(u);
            return saved;
        }
    }

    public Optional<AppUser> findByEmail(String email) {
        return repo.findByEmail(email);
    }

    public AppUser getById(UUID id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
