package com.example.todo.web;

import com.example.todo.web.AiController.AiResponse;
import com.example.todo.web.AiController.AiResponse.Proposal;
import com.example.todo.dto.TaskPatchDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AiControllerUnitTest {

    private final AiController ctrl = new AiController();

    private Proposal call(Map<String, Object> body) {
        ResponseEntity<AiResponse> resp = ctrl.interpret(body);
        assertEquals(200, resp.getStatusCodeValue(), "HTTP 200 expected");
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().proposal);
        return resp.getBody().proposal;
    }

    // -------- базові кейси

    @Test
    @DisplayName("400 на пустому тексті")
    void bad_request_on_blank() {
        Map<String, Object> body = Map.of("text", "   ");
        ResponseEntity<AiResponse> resp = ctrl.interpret(body);
        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    @DisplayName("Happy path: теги, !high, ISO дата, done, title Call X")
    void full_happy_path() {
        Proposal p = call(Map.of("text", "call Alice #work !high 2025-12-31 done"));
        TaskPatchDto t = p.task_patch;

        assertEquals("Rule-based heuristics", p.reason);
        assertTrue(p.confidence > 0.8);

        assertEquals("Call Alice", t.title);
        assertEquals("High", t.priority);
        assertNotNull(t.dueDate);
        assertTrue(t.completed);
        assertNotNull(t.tags);
        assertEquals(List.of("work"), t.tags);
        // ISO-дата о 00:00Z
        assertEquals(0, t.dueDate.getHour());
        assertEquals(0, t.dueDate.getMinute());
        assertEquals(0, t.dueDate.getSecond());
        assertEquals("Z", t.dueDate.getOffset().toString());
    }

    // -------- пріоритети

    @Test
    @DisplayName("!med -> Medium")
    void priority_medium() {
        Proposal p = call(Map.of("text", "email Bob !med 2031-02-02"));
        assertEquals("Medium", p.task_patch.priority);
        assertTrue(p.task_patch.title.startsWith("Email "));
    }

    @Test
    @DisplayName("!med + !low -> Medium (порядок перевірок зберігає Medium)")
    void priority_med_over_low() {
        Proposal p = call(Map.of("text", "buy milk !low !med"));
        assertEquals("Medium", p.task_patch.priority);
        assertTrue(p.task_patch.title.startsWith("Buy "));
    }

    // -------- дати: tomorrow/today та пріоритет ISO над ключовими словами

    @Test
    @DisplayName("tomorrow -> 09:00:00Z")
    void due_tomorrow_9am() {
        Proposal p = call(Map.of("text", "call Carol tomorrow !low"));
        OffsetDateTime d = p.task_patch.dueDate;
        assertNotNull(d);
        assertEquals(9, d.getHour());
        assertEquals(0, d.getMinute());
        assertEquals(0, d.getSecond());
        assertEquals("Z", d.getOffset().toString());
    }

    @Test
    @DisplayName("today -> 18:00:00Z")
    void due_today_18() {
        Proposal p = call(Map.of("text", "email Alice today"));
        OffsetDateTime d = p.task_patch.dueDate;
        assertNotNull(d);
        assertEquals(18, d.getHour());
        assertEquals(0, d.getMinute());
        assertEquals(0, d.getSecond());
        assertEquals("Z", d.getOffset().toString());
    }

    @Test
    @DisplayName("ISO дата перемагає 'tomorrow'")
    void explicit_date_wins_over_tomorrow() {
        Proposal p = call(Map.of("text", "buy tickets 2032-03-03 tomorrow !high"));
        OffsetDateTime d = p.task_patch.dueDate;
        assertEquals(2032, d.getYear());
        assertEquals(3, d.getMonthValue());
        assertEquals(3, d.getDayOfMonth());
        assertEquals(0, d.getHour());
    }

    // -------- completed: done vs todo

    @Test
    @DisplayName("'done' перекриває 'todo'")
    void done_overrides_todo() {
        Proposal p = call(Map.of("text", "todo done call Max"));
        assertTrue(p.task_patch.completed);
    }

    // -------- тайтли

    @Test
    @DisplayName("Email/Buy заголовки")
    void email_and_buy_titles() {
        assertTrue(call(Map.of("text", "email support !low")).task_patch.title.startsWith("Email "));
        assertTrue(call(Map.of("text", "buy adapter !med")).task_patch.title.startsWith("Buy "));
    }

    @Test
    @DisplayName("Fallback title: >60 -> '...' і довжина рівно 63; <=80 дає заголовок")
    void fallback_title_truncate() {
        String seventy = "a".repeat(70);
        Proposal p = call(Map.of("text", seventy)); // без ключових слів, спрацює fallback
        String title = p.task_patch.title;
        assertNotNull(title);
        assertTrue(title.endsWith("..."));
        assertEquals(63, title.length()); // 60 символів + "..."
    }

    @Test
    @DisplayName("Fallback title: >80 -> не встановлюємо")
    void fallback_title_gt80_not_set() {
        String eightyOne = "x".repeat(81) + " #tag"; // теги відсікаються, але ядро довше 80
        Proposal p = call(Map.of("text", eightyOne));
        assertNull(p.task_patch.title);
        // Теги при цьому зчитані
        assertNotNull(p.task_patch.tags);
        assertEquals(List.of("tag"), p.task_patch.tags);
    }

    @Test
    @DisplayName("extractWordAfter: 'call' без об’єкта -> 'Call '")
    void call_without_object() {
        Proposal p = call(Map.of("text", "call   "));
        assertEquals("Call ", p.task_patch.title);
    }

    @Test
    @DisplayName("extractWordAfter: split по пробілам/комам/крапкам")
    void extract_split_variants() {
        Proposal p = call(Map.of("text", "email  Alice, please"));
        assertEquals("Email Alice", p.task_patch.title);
    }

    // -------- теги та confidence без бонусу за тег

    @Test
    @DisplayName("2 теги з #tag синтаксису")
    void tags_two() {
        Proposal p = call(Map.of("text", "x".repeat(10) + " #one #Two todo 2030-01-01"));
        assertNotNull(p.task_patch.tags);
        assertEquals(2, p.task_patch.tags.size());
        assertEquals(List.of("one", "Two"), p.task_patch.tags);
        assertFalse(p.task_patch.completed);
        assertNotNull(p.task_patch.dueDate);
    }

    @Test
    @DisplayName("Без тегів: точна confidence = 0.7 (убиває мутанта з фальшбонусом)")
    void confidence_exact_without_tags() {
        // Розрахунок:
        // !high (0.2) + ISO (0.15) + done (0.05) + title 'Call ...' (0.1) = 0.5
        // confidence = 0.2 + 0.5 = 0.7
        Proposal p = call(Map.of("text", "call Bob !high 2025-12-31 done"));
        assertEquals(0.7, p.confidence, 1e-9);
        assertNull(p.task_patch.tags); // переконуємося, що бонус за теги не застосовано
    }

    // -------- негативні гілки extractWordAfter (idx < 0) — опосередковано через відсутність ключового слова

    @Test
    @DisplayName("Коли немає ключових слів (call/email/buy) — використовується лише fallback")
    void no_keywords_only_fallback() {
        String base = "Plan roadmap for Q1"; // <=80 і без тегів
        Proposal p = call(Map.of("text", base));
        assertEquals(base, p.task_patch.title);
    }
}
