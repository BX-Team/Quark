package org.bxteam.quark.platform;

import org.bxteam.quark.common.JulLogger;
import org.bxteam.quark.common.QuarkLogger;
import org.bxteam.quark.common.SemanticVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class PlatformDetectTest {
    @Test
    void picksSupportingProviderWithHighestPriority() {
        Platform platform = Platform.detect(new TestPlugin());

        assertEquals("high", platform.id());
    }

    @Test
    void failsWhenNothingSupportsThePlugin() {
        PlatformException e = assertThrows(PlatformException.class, () -> Platform.detect("not a plugin"));

        assertTrue(e.getMessage().contains("java.lang.String"));
    }

    static final class TestPlugin {
    }

    public static final class LowPriorityProvider implements PlatformProvider {
        @Override
        public boolean supports(@NotNull Object plugin) {
            return plugin instanceof TestPlugin;
        }

        @Override
        public @NotNull Platform create(@NotNull Object plugin) {
            return new TestPlatform("low");
        }
    }

    public static final class HighPriorityProvider implements PlatformProvider {
        @Override
        public boolean supports(@NotNull Object plugin) {
            return plugin instanceof TestPlugin;
        }

        @Override
        public @NotNull Platform create(@NotNull Object plugin) {
            return new TestPlatform("high");
        }

        @Override
        public int priority() {
            return 10;
        }
    }

    public static final class UnsupportedProvider implements PlatformProvider {
        @Override
        public boolean supports(@NotNull Object plugin) {
            return false;
        }

        @Override
        public @NotNull Platform create(@NotNull Object plugin) {
            throw new AssertionError("must not be called");
        }

        @Override
        public int priority() {
            return 100;
        }
    }

    private record TestPlatform(String id) implements Platform {
        @Override
        public @NotNull PlatformType type() {
            return PlatformType.PAPER;
        }

        @Override
        public @NotNull Path dataDirectory() {
            return Path.of("data");
        }

        @Override
        public @NotNull QuarkLogger logger() {
            return JulLogger.of("test");
        }

        @Override
        public @NotNull Scheduler scheduler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull PlatformVersion version() {
            return new PlatformVersion("Test", SemanticVersion.parse("1.0.0"), null);
        }
    }
}
