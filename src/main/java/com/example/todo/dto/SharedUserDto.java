// src/main/java/com/example/todo/dto/SharedUserDto.java
package com.example.todo.dto;

import com.example.todo.entity.enums.ShareRole;

public class SharedUserDto {
    public String email;
    public ShareRole role; // viewer | editor
    public SharedUserDto() {}
    public SharedUserDto(String email, ShareRole role) { this.email = email; this.role = role; }
}
