package org.bxteam.quark.testing;

import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.LogLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoopLogAdapter implements LogAdapter {
    @Override
    public void log(@NotNull LogLevel level, @NotNull String message) {
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable throwable) {
    }
}
