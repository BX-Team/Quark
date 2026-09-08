package org.bxteam.quark.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import org.bxteam.quark.platform.Scheduler;
import org.bxteam.quark.platform.TaskHandle;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * {@link Scheduler} on top of the BungeeCord scheduler. BungeeCord has no main thread, so "sync" tasks run on
 * the scheduler pool exactly like async ones.
 */
public class BungeeTaskScheduler implements Scheduler {
    private final Plugin plugin;

    /**
     * @param plugin the plugin that owns the tasks
     */
    public BungeeTaskScheduler(@NotNull Plugin plugin) {
        this.plugin = requireNonNull(plugin, "Plugin cannot be null");
    }

    @Override
    @NotNull
    public TaskHandle async(@NotNull Runnable task) {
        return handle(plugin.getProxy().getScheduler().runAsync(plugin, requireNonNull(task, "Task cannot be null")));
    }

    @Override
    @NotNull
    public TaskHandle sync(@NotNull Runnable task) {
        return async(task);
    }

    @Override
    @NotNull
    public TaskHandle later(@NotNull Runnable task, @NotNull Duration delay) {
        return handle(plugin.getProxy().getScheduler().schedule(plugin, requireNonNull(task, "Task cannot be null"),
                delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    @NotNull
    public TaskHandle repeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        return handle(plugin.getProxy().getScheduler().schedule(plugin, requireNonNull(task, "Task cannot be null"),
                delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS));
    }

    private static TaskHandle handle(ScheduledTask task) {
        // the BungeeCord API cannot tell whether a task was cancelled, so the handle remembers it
        AtomicBoolean cancelled = new AtomicBoolean();
        return new TaskHandle() {
            @Override
            public void cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    task.cancel();
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };
    }
}
