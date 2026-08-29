package org.bxteam.quark.paper;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bxteam.quark.bukkit.BukkitTaskScheduler;
import org.bxteam.quark.platform.Scheduler;
import org.bxteam.quark.platform.TaskHandle;
import org.jetbrains.annotations.NotNull;

/**
 * Paper scheduler with entity- and location-bound overloads.
 *
 * <p>On servers with the region scheduler API (Folia, and Paper since 1.20) sync tasks without context
 * run on the global region scheduler, which is the main thread on Paper and the global region thread on
 * Folia. Code that touches an entity or blocks must use {@link #sync(Entity, Runnable)} or
 * {@link #sync(Location, Runnable)}, which run on the thread owning that entity or region. On older Paper
 * versions every task goes through the Bukkit scheduler.</p>
 */
public interface PaperScheduler extends Scheduler {
    /**
     * Runs the task on the thread that owns the entity. If the entity is removed before the task runs,
     * the task is skipped and the handle reports it as cancelled.
     *
     * @param entity the entity
     * @param task the task
     * @return the task handle
     */
    @NotNull
    TaskHandle sync(@NotNull Entity entity, @NotNull Runnable task);

    /**
     * Runs the task on the thread that owns the region containing the location.
     *
     * @param location the location
     * @param task the task
     * @return the task handle
     */
    @NotNull
    TaskHandle sync(@NotNull Location location, @NotNull Runnable task);

    /**
     * Creates the scheduler best suited for the running server.
     *
     * @param plugin the plugin that owns the tasks
     * @return the scheduler
     */
    @NotNull
    static PaperScheduler create(@NotNull Plugin plugin) {
        return RegionPaperScheduler.isAvailable() ? new RegionPaperScheduler(plugin) : new LegacyPaperScheduler(plugin);
    }

    /**
     * Fallback for Paper versions without the region scheduler API.
     */
    final class LegacyPaperScheduler extends BukkitTaskScheduler implements PaperScheduler {
        private LegacyPaperScheduler(@NotNull Plugin plugin) {
            super(plugin);
        }

        @Override
        @NotNull
        public TaskHandle sync(@NotNull Entity entity, @NotNull Runnable task) {
            return sync(task);
        }

        @Override
        @NotNull
        public TaskHandle sync(@NotNull Location location, @NotNull Runnable task) {
            return sync(task);
        }
    }
}
