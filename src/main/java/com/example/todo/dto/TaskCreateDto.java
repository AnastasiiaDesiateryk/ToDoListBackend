// src/main/java/com/example/todo/dto/TaskCreateDto.java
package com.example.todo.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class TaskCreateDto {
    @NotBlank public String title;
    public String description;
    public String category;           // "Work" | "Personal"
    public String priority;           // "High" | "Medium" | "Low"
    public OffsetDateTime dueDate;    // <== как во фронте
    public Boolean completed;         // <== как во фронте
    public List<String> tags;
    public String source;
    public Map<String,Object> metadata;
}
