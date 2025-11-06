// src/test/java/com/example/todo/mapper/TaskMapperTest.java
package com.example.todo.mapper;

import com.example.todo.dto.TaskCreateDto;
// imported for clarity and IDE type resolution
import com.example.todo.dto.TaskDto;
import com.example.todo.dto.TaskPatchDto;
import com.example.todo.entity.AppUser;
import com.example.todo.entity.Task;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TaskMapper}.
 * Verifies DTO↔Entity mapping, priority/status conversions, and JSON metadata handling.
 */
@Tag("unit")
class TaskMapperTest {

    private final TaskMapper mapper = Mappers.getMapper(TaskMapper.class);

    @Test
    @DisplayName("Entity → DTO maps core fields and converts enums correctly")
    void toDto_maps_fields_and_enums() {
        var user = new AppUser(); user.setEmail("alice@example.com");
        var entity = new Task();
        entity.setTitle("Design API");
        entity.setDescription("REST spec");
        entity.setPriority(TaskPriority.HIGH);
        entity.setStatus(TaskStatus.DONE);
        entity.setOwner(user);
        entity.setMetadata("{\"k\":\"v\"}");

        var dto = mapper.toDto(entity);

        assertThat(dto.title).isEqualTo("Design API");
        assertThat(dto.completed).isTrue();
        assertThat(dto.priority).isEqualTo("High");
        assertThat(dto.ownerEmail).isEqualTo("alice@example.com");
        assertThat(dto.metadata).containsEntry("k", "v");
    }

    @Test
    @DisplayName("Create DTO → Entity sets defaults and serializes metadata")
    void toEntity_maps_fields_and_defaults() {
        var dto = new TaskCreateDto();
        dto.title = "Implement feature";
        dto.description = "Backend part";
        dto.priority = "Low";
        dto.completed = false;
        dto.metadata = Map.of("a", 1, "b", "x");
        dto.dueDate = OffsetDateTime.now();

        var entity = mapper.toEntity(dto);

        assertThat(entity.getTitle()).isEqualTo("Implement feature");
        assertThat(entity.getPriority()).isEqualTo(TaskPriority.LOW);
        assertThat(entity.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(entity.getMetadata()).contains("\"a\":1");
        assertThat(entity.getMetadata()).contains("\"b\":\"x\"");
    }

    @Test
    @DisplayName("Patch DTO → existing Entity updates only non-null fields")
    void updateFromPatch_applies_partial_changes() {
        var entity = new Task();
        entity.setTitle("Old title");
        entity.setPriority(TaskPriority.MED);
        entity.setStatus(TaskStatus.TODO);
        entity.setMetadata("{\"x\":1}");

        var patch = new TaskPatchDto();
        patch.title = "New title";
        patch.priority = "High";         // will override
        patch.completed = true;          // maps to DONE
        patch.metadata = Map.of("y", 2); // override JSON

        mapper.updateFromPatch(patch, entity);

        assertThat(entity.getTitle()).isEqualTo("New title");
        assertThat(entity.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(entity.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(entity.getMetadata()).contains("\"y\":2");
    }

    @Test
    @DisplayName("Priority mapping between enum and UI values is bidirectional and case-insensitive")
    void priority_mapping_is_consistent() {
        assertThat(mapper.toUiPriority(TaskPriority.HIGH)).isEqualTo("High");
        assertThat(mapper.toUiPriority(TaskPriority.LOW)).isEqualTo("Low");
        assertThat(mapper.toUiPriority(TaskPriority.MED)).isEqualTo("Medium");

        assertThat(mapper.toDbPriority("high")).isEqualTo(TaskPriority.HIGH);
        assertThat(mapper.toDbPriority("LOW")).isEqualTo(TaskPriority.LOW);
        assertThat(mapper.toDbPriority("whatever")).isEqualTo(TaskPriority.MED);
    }

    @Test
    @DisplayName("Status ↔ completed conversion is consistent")
    void status_completed_conversion() {
        assertThat(mapper.toUiCompleted(TaskStatus.DONE)).isTrue();
        assertThat(mapper.toUiCompleted(TaskStatus.TODO)).isFalse();

        assertThat(mapper.toDbStatus(true)).isEqualTo(TaskStatus.DONE);
        assertThat(mapper.toDbStatus(false)).isEqualTo(TaskStatus.TODO);
    }

    @Test
    @DisplayName("JSON metadata round-trip: Map → String → Map")
    void json_metadata_roundtrip() {
        Map<String, Object> map = Map.of("key", "value", "num", 42);
        var json = mapper.mapToJson(map);
        assertThat(json).contains("\"key\":\"value\"").contains("\"num\":42");

        var back = mapper.jsonToMap(json);
        assertThat(back).containsEntry("key", "value").containsEntry("num", 42);
    }

    @Test
    @DisplayName("Invalid JSON input throws RuntimeException")
    void invalid_json_throws() {
        assertThatThrownBy(() -> mapper.jsonToMap("{broken-json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse");
    }
}
