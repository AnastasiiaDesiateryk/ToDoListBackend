// src/main/java/com/example/todo/dto/TaskDto.java
package com.example.todo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDto {
    public UUID id;
    public String title;
    public String description;
    public String category;
    public String priority;        // "High" | "Medium" | "Low"
    public OffsetDateTime dueDate; // <== фронтовое имя
    public Boolean completed;      // <== фронт
    public String status;          // "TODO"  | "DONE" (для дебага/фильтров)
    public List<String> tags;
    public String source;
    public Map<String,Object> metadata;
    public UUID ownerId;
    public String ownerEmail;
    public Integer version;
    public OffsetDateTime createdAt;  // фронт показывает
    public OffsetDateTime updatedAt;
}
