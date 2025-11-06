package com.example.todo.security;

import java.util.UUID;

public class UserPrincipal {
    private final UUID id;
    private final String email;
    private final String displayName;

    public UserPrincipal(UUID id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
}
