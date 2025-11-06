// src/test/java/com/example/todo/util/ETagUtilTest.java
package com.example.todo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ETagUtilTest {

    @Test
    void formatWeak_wrapsVersionProperly() {
        assertEquals("W/\"5\"", ETagUtil.formatWeak(5));
        assertNull(ETagUtil.formatWeak(null));
    }

    @Test
    void parseIfMatch_returnsNullOnNull() {
        assertNull(ETagUtil.parseIfMatch(null)); // kills mutant: line 11 -> return 0 / drop null-check
    }

    @Test
    void parseIfMatch_parsesWeakAndStrong() {
        assertEquals(5, ETagUtil.parseIfMatch("W/\"5\""));
        assertEquals(5, ETagUtil.parseIfMatch("\"5\""));
        assertEquals(5, ETagUtil.parseIfMatch("  W/\"5\"  "));
    }

    @Test
    void parseIfMatch_rejectsInvalidFormats() {
        // no quotes after W/ -> invalid (covers i1<0||i2<=i1 branch)
        assertThrows(IllegalArgumentException.class, () -> ETagUtil.parseIfMatch("W/5"));

        // non number inside quotes
        assertThrows(IllegalArgumentException.class, () -> ETagUtil.parseIfMatch("W/\"abc\""));

        // plain non-number
        assertThrows(IllegalArgumentException.class, () -> ETagUtil.parseIfMatch("not-a-number"));
    }

    // ---- boundary / unbalanced quotes to kill survivors on lines 16 and 21 ----

    @Test
    void parseIfMatch_weakUnbalanced_missingRightQuote() {
        assertThrows(IllegalArgumentException.class, () -> ETagUtil.parseIfMatch("W/\"5"));
    }

    @Test
    void parseIfMatch_weakUnbalanced_missingLeftQuote() {
        assertThrows(IllegalArgumentException.class, () -> ETagUtil.parseIfMatch("W/5\""));
    }

    @Test
    void parseIfMatch_weakEmptyQuotes() {
        // substring("") -> NumberFormatException -> IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> ETagUtil.parseIfMatch("W/\"\""));
    }

    @Test
    void parseIfMatch_strongUnbalanced_onlyLeftQuote() {
        assertThrows(IllegalArgumentException.class, () -> ETagUtil.parseIfMatch("\"5"));
    }

    @Test
    void parseIfMatch_strongUnbalanced_onlyRightQuote() {
        assertThrows(IllegalArgumentException.class, () -> ETagUtil.parseIfMatch("5\""));
    }
}
