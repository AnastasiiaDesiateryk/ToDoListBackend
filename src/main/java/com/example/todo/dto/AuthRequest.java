// src/main/java/com/example/todo/dto/AuthRequest.java
package com.example.todo.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(@NotBlank String idToken) {}
