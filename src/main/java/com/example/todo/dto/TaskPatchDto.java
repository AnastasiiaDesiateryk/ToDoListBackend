// src/main/java/com/example/todo/dto/TaskPatchDto.java
package com.example.todo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskPatchDto {
    public String title;
    public String description;
    public String category;
    public String priority;           // "High" | "Medium" | "Low"
    public OffsetDateTime dueDate;    // <== как во фронте
    public Boolean completed;         // <== как во фронте
    public List<String> tags;
    public String source;
    public Map<String,Object> metadata;
}
