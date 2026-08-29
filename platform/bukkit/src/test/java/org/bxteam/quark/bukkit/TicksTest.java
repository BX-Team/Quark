package org.bxteam.quark.bukkit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicksTest {
    @Test
    void roundsUpToWholeTicks() {
        assertEquals(0, Ticks.of(Duration.ZERO));
        assertEquals(1, Ticks.of(Duration.ofMillis(1)));
        assertEquals(1, Ticks.of(Duration.ofMillis(50)));
        assertEquals(2, Ticks.of(Duration.ofMillis(51)));
        assertEquals(20, Ticks.of(Duration.ofSeconds(1)));
    }

    @Test
    void rejectsNegativeDurations() {
        assertThrows(IllegalArgumentException.class, () -> Ticks.of(Duration.ofMillis(-1)));
    }
}
