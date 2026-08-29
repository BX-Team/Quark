package org.bxteam.quark.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bxteam.quark.platform.Scheduler;
import org.bxteam.quark.platform.TaskHandle;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * {@link Scheduler} on top of the Bukkit scheduler. Sync tasks run on the main server thread.
 */
public class BukkitTaskScheduler implements Scheduler {
    private final Plugin plugin;

    /**
     * @param plugin the plugin that owns the tasks
     */
    public BukkitTaskScheduler(@NotNull Plugin plugin) {
        this.plugin = requireNonNull(plugin, "Plugin cannot be null");
    }

    @Override
    @NotNull
    public TaskHandle async(@NotNull Runnable task) {
        return handle(Bukkit.getScheduler().runTaskAsynchronously(plugin, requireNonNull(task, "Task cannot be null")));
    }

    @Override
    @NotNull
    public TaskHandle sync(@NotNull Runnable task) {
        return handle(Bukkit.getScheduler().runTask(plugin, requireNonNull(task, "Task cannot be null")));
    }

    @Override
    @NotNull
    public TaskHandle later(@NotNull Runnable task, @NotNull Duration delay) {
        return handle(Bukkit.getScheduler().runTaskLater(plugin, requireNonNull(task, "Task cannot be null"), Ticks.of(delay)));
    }

    @Override
    @NotNull
    public TaskHandle repeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        return handle(Bukkit.getScheduler().runTaskTimer(plugin, requireNonNull(task, "Task cannot be null"),
                Ticks.of(delay), Ticks.of(period)));
    }

    private static TaskHandle handle(BukkitTask task) {
        return new TaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }
}
