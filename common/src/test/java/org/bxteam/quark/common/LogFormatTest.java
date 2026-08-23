package org.bxteam.quark.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFormatTest {
    @Test
    void replacesPlaceholdersInOrder() {
        assertEquals("a=1, b=2", LogFormat.format("a={}, b={}", 1, 2));
    }

    @Test
    void keepsUnmatchedPlaceholders() {
        assertEquals("a=1, b={}", LogFormat.format("a={}, b={}", 1));
        assertEquals("no args {}", LogFormat.format("no args {}"));
    }

    @Test
    void ignoresExtraArguments() {
        assertEquals("a=1", LogFormat.format("a={}", 1, 2));
    }
}
