package com.example.todo.mapper;

import com.example.todo.dto.TaskCreateDto;
import com.example.todo.dto.TaskDto;
import com.example.todo.dto.TaskPatchDto;
import com.example.todo.entity.Task;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    // ===== Entity → DTO =====
    @Mapping(target = "dueDate", source = "dueAt")
    @Mapping(target = "priority", expression = "java(toUiPriority(entity.getPriority()))")
    @Mapping(target = "completed", expression = "java(toUiCompleted(entity.getStatus()))")
    @Mapping(target = "ownerId", source = "owner.id")
    @Mapping(target = "ownerEmail", source = "owner.email") // 👈 это новенькое
    @Mapping(target = "status", expression = "java(entity.getStatus() == null ? null : entity.getStatus().name())")
    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "jsonToMap")
    TaskDto toDto(Task entity);

    // ===== Create DTO → Entity =====
    @Mapping(target = "dueAt", source = "dueDate")
    @Mapping(target = "priority", expression = "java(toDbPriority(dto.priority))")
    @Mapping(target = "status", expression = "java(toDbStatus(dto.completed))")
    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "mapToJson")

    // служебные поля — игнор
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Task toEntity(TaskCreateDto dto);

    // ===== Patch DTO → Entity =====
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "dueAt", source = "dueDate")
    @Mapping(target = "priority",
            expression = "java(patch.priority == null ? entity.getPriority() : toDbPriority(patch.priority))")
    @Mapping(target = "status",
            expression = "java(patch.completed == null ? entity.getStatus() : toDbStatus(patch.completed))")
    @Mapping(target = "metadata",
            expression = "java(patch.metadata == null ? entity.getMetadata() : mapToJson(patch.metadata))")

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateFromPatch(TaskPatchDto patch, @MappingTarget Task entity);

    // ===== Приоритеты =====
    default String toUiPriority(TaskPriority p) {
        if (p == null) return "Medium";
        return switch (p) { case HIGH -> "High"; case LOW -> "Low"; default -> "Medium"; };
    }
    default TaskPriority toDbPriority(String ui) {
        if (ui == null) return TaskPriority.MED;
        return switch (ui.toLowerCase()) { case "high" -> TaskPriority.HIGH; case "low" -> TaskPriority.LOW; default -> TaskPriority.MED; };
    }

    // ===== Статус / completed =====
    default Boolean toUiCompleted(TaskStatus s) { return s != null && s == TaskStatus.DONE; }
    default TaskStatus toDbStatus(Boolean completed) { return completed != null && completed ? TaskStatus.DONE : TaskStatus.TODO; }

    // ===== metadata JSON ↔ Map =====
    @Named("jsonToMap")
    default Map<String, Object> jsonToMap(String json) {
        if (json == null) return null;
        try {
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse metadata JSON", e);
        }
    }

    @Named("mapToJson")
    default String mapToJson(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return new ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize metadata map", e);
        }
    }
}
