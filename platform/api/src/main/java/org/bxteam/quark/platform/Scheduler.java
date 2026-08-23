package org.bxteam.quark.platform;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Platform scheduler bound to a plugin.
 *
 * <p><b>Thread of "sync" tasks.</b> {@link #sync(Runnable)}, {@link #later(Runnable, Duration)} and
 * {@link #repeating(Runnable, Duration, Duration)} run on:</p>
 * <ul>
 *   <li>the main server thread on Bukkit and Paper;</li>
 *   <li>the <b>global region</b> thread on Folia. It is safe for world-independent state (game rules,
 *       console commands, plugin state) but not for entities or blocks: use the entity/location
 *       overloads of the Paper adapter's scheduler for those;</li>
 *   <li>the proxy's scheduler pool on BungeeCord and Velocity, which have no main thread.</li>
 * </ul>
 *
 * <p>On Bukkit-based platforms durations are rounded up to whole ticks (50 ms).</p>
 */
public interface Scheduler {
    /**
     * Runs the task off the server thread as soon as possible.
     *
     * @param task the task
     * @return the task handle
     */
    @NotNull
    TaskHandle async(@NotNull Runnable task);

    /**
     * Runs the task on the next tick of the sync thread (see class description).
     *
     * @param task the task
     * @return the task handle
     */
    @NotNull
    TaskHandle sync(@NotNull Runnable task);

    /**
     * Runs the task on the sync thread after a delay.
     *
     * @param task the task
     * @param delay the delay
     * @return the task handle
     */
    @NotNull
    TaskHandle later(@NotNull Runnable task, @NotNull Duration delay);

    /**
     * Runs the task on the sync thread repeatedly until cancelled.
     *
     * @param task the task
     * @param delay the delay before the first run
     * @param period the time between runs
     * @return the task handle
     */
    @NotNull
    TaskHandle repeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period);

    /**
     * Bridge for library modules that accept a plain {@link Executor}: every submitted task
     * is passed to {@link #async(Runnable)}.
     *
     * @return the executor
     */
    @NotNull
    default Executor asExecutor() {
        return this::async;
    }
}
