package org.bxteam.quark.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;
import org.bxteam.quark.platform.Scheduler;
import org.bxteam.quark.platform.TaskHandle;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * {@link Scheduler} on top of the Velocity scheduler. Velocity has no main thread, so "sync" tasks run on
 * the scheduler pool exactly like async ones.
 */
final class VelocityScheduler implements Scheduler {
    private final ProxyServer server;
    private final Object plugin;

    VelocityScheduler(@NotNull ProxyServer server, @NotNull Object plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public TaskHandle async(@NotNull Runnable task) {
        return handle(server.getScheduler().buildTask(plugin, requireNonNull(task, "Task cannot be null")).schedule());
    }

    @Override
    @NotNull
    public TaskHandle sync(@NotNull Runnable task) {
        return async(task);
    }

    @Override
    @NotNull
    public TaskHandle later(@NotNull Runnable task, @NotNull Duration delay) {
        return handle(server.getScheduler().buildTask(plugin, requireNonNull(task, "Task cannot be null"))
                .delay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .schedule());
    }

    @Override
    @NotNull
    public TaskHandle repeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        return handle(server.getScheduler().buildTask(plugin, requireNonNull(task, "Task cannot be null"))
                .delay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .repeat(period.toMillis(), TimeUnit.MILLISECONDS)
                .schedule());
    }

    private static TaskHandle handle(ScheduledTask task) {
        return new TaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.status() == TaskStatus.CANCELLED;
            }
        };
    }
}
