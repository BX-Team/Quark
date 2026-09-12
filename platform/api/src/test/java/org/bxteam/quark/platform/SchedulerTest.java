package org.bxteam.quark.platform;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {
    @Test
    void callSyncCompletesWithResult() throws Exception {
        assertEquals(42, new ImmediateScheduler().callSync(() -> 42).get());
    }

    @Test
    void callSyncCompletesExceptionally() {
        IllegalStateException failure = new IllegalStateException("boom");

        ExecutionException e = assertThrows(ExecutionException.class, () -> new ImmediateScheduler().callSync(() -> {
            throw failure;
        }).get());
        assertSame(failure, e.getCause());
    }

    @Test
    void asExecutorRunsAsync() {
        ImmediateScheduler scheduler = new ImmediateScheduler();
        scheduler.asExecutor().execute(() -> {
        });

        assertEquals(1, scheduler.asyncCalls);
    }

    /** Runs everything immediately on the calling thread. */
    private static final class ImmediateScheduler implements Scheduler {
        int asyncCalls;

        private TaskHandle run(Runnable task) {
            task.run();
            return new TaskHandle() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }

        @Override
        public @NotNull TaskHandle async(@NotNull Runnable task) {
            asyncCalls++;
            return run(task);
        }

        @Override
        public @NotNull TaskHandle sync(@NotNull Runnable task) {
            return run(task);
        }

        @Override
        public @NotNull TaskHandle later(@NotNull Runnable task, @NotNull Duration delay) {
            return run(task);
        }

        @Override
        public @NotNull TaskHandle repeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
            return run(task);
        }

        @Override
        public @NotNull TaskHandle asyncLater(@NotNull Runnable task, @NotNull Duration delay) {
            return run(task);
        }

        @Override
        public @NotNull TaskHandle asyncRepeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
            return run(task);
        }

        @Override
        public boolean isSyncThread() {
            return true;
        }

        @Override
        public void cancelAll() {
        }
    }
}
