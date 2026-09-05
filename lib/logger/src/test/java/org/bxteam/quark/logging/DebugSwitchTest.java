package org.bxteam.quark.logging;

import org.bxteam.quark.common.LogFormat;
import org.bxteam.quark.common.QuarkLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class DebugSwitchTest {
    @AfterEach
    void reset() {
        DebugSwitch.setEnabled(false);
    }

    @Test
    void debugGoesToBackendWhileDisabled() {
        RecordingLogger backend = new RecordingLogger("");
        QuarkLogger logger = DebugSwitch.wrap(backend);

        logger.debug(() -> "details");

        assertEquals(List.of("DEBUG details"), backend.lines);
    }

    @Test
    void debugIsPrintedAtInfoWhileEnabled() {
        RecordingLogger backend = new RecordingLogger("");
        QuarkLogger logger = DebugSwitch.wrap(backend).prefixed("db");
        DebugSwitch.setEnabled(true);

        logger.debug(() -> "query {} took 3 ms");

        assertEquals(List.of("INFO [db] [DEBUG] query {} took 3 ms"), backend.lines);
    }

    @Test
    void supplierIsNotCalledWhenNobodyListens() {
        QuarkLogger logger = DebugSwitch.wrap(new RecordingLogger("") {
            @Override
            public void debug(@NotNull Supplier<String> message) {
            }
        });

        logger.debug(() -> fail("must not be evaluated"));
    }

    @Test
    void wrappingTwiceKeepsOneLayer() {
        QuarkLogger once = DebugSwitch.wrap(new RecordingLogger(""));

        assertSame(once, DebugSwitch.wrap(once));
    }

    private static class RecordingLogger implements QuarkLogger {
        final List<String> lines;
        final String prefix;

        RecordingLogger(String prefix) {
            this(prefix, new ArrayList<>());
        }

        RecordingLogger(String prefix, List<String> lines) {
            this.prefix = prefix;
            this.lines = lines;
        }

        @Override
        public void info(@NotNull String message, Object... args) {
            lines.add("INFO " + prefix + LogFormat.format(message, args));
        }

        @Override
        public void warn(@NotNull String message, Object... args) {
            lines.add("WARN " + prefix + LogFormat.format(message, args));
        }

        @Override
        public void error(@NotNull String message, @Nullable Throwable t) {
            lines.add("ERROR " + prefix + message);
        }

        @Override
        public void debug(@NotNull Supplier<String> message) {
            lines.add("DEBUG " + prefix + message.get());
        }

        @Override
        public @NotNull QuarkLogger prefixed(@NotNull String prefix) {
            return new RecordingLogger(this.prefix + "[" + prefix + "] ", lines);
        }
    }
}
