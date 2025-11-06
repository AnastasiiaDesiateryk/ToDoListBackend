package com.example.todo.web;

import com.example.todo.dto.TaskPatchDto;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/ai")
@Validated
public class AiController {

    @PostMapping("/interpret")
    public ResponseEntity<AiResponse> interpret(@RequestBody @NotBlank Map<String, Object> body) {
        String text = Objects.toString(body.get("text"), "");
        if (text.isBlank()) return ResponseEntity.badRequest().build();

        TaskPatchDto patch = new TaskPatchDto();
        double score = 0.0;

        // tags: #tag
        Pattern tagPattern = Pattern.compile("#([A-Za-z0-9_-]+)");
        Matcher m = tagPattern.matcher(text);
        List<String> tags = new ArrayList<>();
        while (m.find()) tags.add(m.group(1));
        if (!tags.isEmpty()) { patch.tags = tags; score += 0.2; }

        // priority tokens -> фронтовые значения "High|Medium|Low"
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("!high"))      { patch.priority = "High"; score += 0.2; }
        else if (lower.contains("!med"))  { patch.priority = "Medium"; score += 0.1; }
        else if (lower.contains("!low"))  { patch.priority = "Low"; score += 0.05; }

        // due date YYYY-MM-DD
        Matcher md = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(text);
        if (md.find()) {
            try {
                String d = md.group(1);
                patch.dueDate = OffsetDateTime.parse(d + "T00:00:00Z"); // <-- dueDate
                score += 0.15;
            } catch (Exception ignored) {}
        } else if (lower.contains("tomorrow")) {
            patch.dueDate = OffsetDateTime.now(ZoneOffset.UTC)
                    .plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
            score += 0.08;
        } else if (lower.contains("today")) {
            patch.dueDate = OffsetDateTime.now(ZoneOffset.UTC)
                    .withHour(18).withMinute(0).withSecond(0).withNano(0);
            score += 0.05;
        }

        // completed hints
        if (lower.contains("done") || lower.contains("complete")) {
            patch.completed = true; score += 0.05;
        } else if (lower.contains("todo")) {
            patch.completed = false; score += 0.02;
        }

        // title heuristic
        if (lower.contains("call")) {
            patch.title = "Call " + extractWordAfter(text, "call");
            score += 0.1;
        } else if (lower.contains("email")) {
            patch.title = "Email " + extractWordAfter(text, "email");
            score += 0.1;
        } else if (lower.contains("buy")) {
            patch.title = "Buy " + extractWordAfter(text, "buy");
            score += 0.1;
        } else {
            String t = text.replaceAll("#[A-Za-z0-9_-]+", "").trim();
            if (!t.isEmpty() && t.length() <= 80) {
                patch.title = t.length() > 60 ? t.substring(0, 60) + "..." : t;
                score += 0.05;
            }
        }

        double confidence = Math.min(0.95, 0.2 + score);

        AiResponse.Proposal p = new AiResponse.Proposal();
        p.task_patch = patch;
        p.reason = "Rule-based heuristics";
        p.confidence = confidence;

        AiResponse resp = new AiResponse();
        resp.proposal = p;
        return ResponseEntity.ok(resp);
    }

    private String extractWordAfter(String text, String keyword) {
        int idx = text.toLowerCase(Locale.ROOT).indexOf(keyword);
        if (idx < 0) return "";
        String after = text.substring(idx + keyword.length()).trim();
        if (after.isEmpty()) return "";
        String[] parts = after.split("[\\s,;.]+");
        return parts.length > 0 ? parts[0] : "";
    }

    public static class AiResponse {
        public Proposal proposal;
        public static class Proposal {
            public TaskPatchDto task_patch;
            public String reason;
            public double confidence;
        }
    }
}
