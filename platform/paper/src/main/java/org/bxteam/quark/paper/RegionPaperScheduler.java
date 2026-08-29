package org.bxteam.quark.paper;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bxteam.quark.bukkit.Ticks;
import org.bxteam.quark.platform.TaskHandle;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * {@link PaperScheduler} on top of the region scheduler API (global region, async, entity and region schedulers).
 */
final class RegionPaperScheduler implements PaperScheduler {
    private static final TaskHandle RETIRED = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    private final Plugin plugin;

    RegionPaperScheduler(@NotNull Plugin plugin) {
        this.plugin = requireNonNull(plugin, "Plugin cannot be null");
    }

    static boolean isAvailable() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    @NotNull
    public TaskHandle async(@NotNull Runnable task) {
        requireNonNull(task, "Task cannot be null");
        return handle(Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run()));
    }

    @Override
    @NotNull
    public TaskHandle sync(@NotNull Runnable task) {
        requireNonNull(task, "Task cannot be null");
        return handle(Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run()));
    }

    @Override
    @NotNull
    public TaskHandle later(@NotNull Runnable task, @NotNull Duration delay) {
        requireNonNull(task, "Task cannot be null");
        long ticks = Ticks.of(delay);
        if (ticks == 0) {
            return sync(task);
        }
        return handle(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), ticks));
    }

    @Override
    @NotNull
    public TaskHandle repeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        requireNonNull(task, "Task cannot be null");
        // the region scheduler requires both values to be at least one tick
        long delayTicks = Math.max(1, Ticks.of(delay));
        long periodTicks = Math.max(1, Ticks.of(period));
        return handle(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.run(), delayTicks, periodTicks));
    }

    @Override
    @NotNull
    public TaskHandle sync(@NotNull Entity entity, @NotNull Runnable task) {
        requireNonNull(entity, "Entity cannot be null");
        requireNonNull(task, "Task cannot be null");
        ScheduledTask scheduled = entity.getScheduler().run(plugin, ignored -> task.run(), null);
        return scheduled != null ? handle(scheduled) : RETIRED;
    }

    @Override
    @NotNull
    public TaskHandle sync(@NotNull Location location, @NotNull Runnable task) {
        requireNonNull(location, "Location cannot be null");
        requireNonNull(task, "Task cannot be null");
        return handle(Bukkit.getRegionScheduler().run(plugin, location, scheduled -> task.run()));
    }

    private static TaskHandle handle(ScheduledTask task) {
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
