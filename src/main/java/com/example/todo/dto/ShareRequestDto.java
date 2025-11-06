package com.example.todo.dto;

import com.example.todo.entity.enums.ShareRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ShareRequestDto {
    @Email @NotBlank
    public String userEmail;

    @NotNull
    public ShareRole role;
}
