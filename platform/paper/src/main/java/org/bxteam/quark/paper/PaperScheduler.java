package org.bxteam.quark.paper;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bxteam.quark.bukkit.BukkitTaskScheduler;
import org.bxteam.quark.platform.Scheduler;
import org.bxteam.quark.platform.TaskHandle;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

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
     * Runs the task on the thread that owns the entity after a delay. Skipped if the entity is removed first.
     *
     * @param entity the entity
     * @param task the task
     * @param delay the delay
     * @return the task handle
     */
    @NotNull
    TaskHandle later(@NotNull Entity entity, @NotNull Runnable task, @NotNull Duration delay);

    /**
     * Runs the task on the thread that owns the region containing the location after a delay.
     *
     * @param location the location
     * @param task the task
     * @param delay the delay
     * @return the task handle
     */
    @NotNull
    TaskHandle later(@NotNull Location location, @NotNull Runnable task, @NotNull Duration delay);

    /**
     * Runs the task on the thread that owns the entity repeatedly until cancelled or the entity is removed.
     *
     * @param entity the entity
     * @param task the task
     * @param delay the delay before the first run
     * @param period the time between runs
     * @return the task handle
     */
    @NotNull
    TaskHandle repeating(@NotNull Entity entity, @NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period);

    /**
     * Runs the task on the thread that owns the region containing the location repeatedly until cancelled.
     *
     * @param location the location
     * @param task the task
     * @param delay the delay before the first run
     * @param period the time between runs
     * @return the task handle
     */
    @NotNull
    TaskHandle repeating(@NotNull Location location, @NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period);

    /**
     * @param entity the entity
     * @return true if the current thread may access the entity (always the main thread outside Folia)
     */
    boolean isOwnedByCurrentThread(@NotNull Entity entity);

    /**
     * @param location the location
     * @return true if the current thread may access the region of the location (always the main thread outside Folia)
     */
    boolean isOwnedByCurrentThread(@NotNull Location location);

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

        @Override
        @NotNull
        public TaskHandle later(@NotNull Entity entity, @NotNull Runnable task, @NotNull Duration delay) {
            return later(task, delay);
        }

        @Override
        @NotNull
        public TaskHandle later(@NotNull Location location, @NotNull Runnable task, @NotNull Duration delay) {
            return later(task, delay);
        }

        @Override
        @NotNull
        public TaskHandle repeating(@NotNull Entity entity, @NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
            return repeating(task, delay, period);
        }

        @Override
        @NotNull
        public TaskHandle repeating(@NotNull Location location, @NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
            return repeating(task, delay, period);
        }

        @Override
        public boolean isOwnedByCurrentThread(@NotNull Entity entity) {
            return isSyncThread();
        }

        @Override
        public boolean isOwnedByCurrentThread(@NotNull Location location) {
            return isSyncThread();
        }
    }
}
