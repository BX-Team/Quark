package org.bxteam.quark.paper;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bxteam.quark.bukkit.Ticks;
import org.bxteam.quark.platform.TaskHandle;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

    /** {@code Server#isGlobalTickThread()}: Folia API, not part of the Paper API Quark compiles against. */
    private static final MethodHandle IS_GLOBAL_TICK_THREAD = findIsGlobalTickThread();

    private final Plugin plugin;

    RegionPaperScheduler(@NotNull Plugin plugin) {
        this.plugin = requireNonNull(plugin, "Plugin cannot be null");
    }

    private static MethodHandle findIsGlobalTickThread() {
        try {
            return MethodHandles.publicLookup().findVirtual(Server.class, "isGlobalTickThread", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException e) {
            return null;
        }
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

    @Override
    @NotNull
    public TaskHandle asyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        requireNonNull(task, "Task cannot be null");
        long millis = millis(delay);
        if (millis == 0) {
            return async(task);
        }
        return handle(Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), millis, TimeUnit.MILLISECONDS));
    }

    @Override
    @NotNull
    public TaskHandle asyncRepeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        requireNonNull(task, "Task cannot be null");
        // the async scheduler requires a positive period
        return handle(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(),
                millis(delay), Math.max(1, millis(period)), TimeUnit.MILLISECONDS));
    }

    @Override
    @NotNull
    public TaskHandle later(@NotNull Entity entity, @NotNull Runnable task, @NotNull Duration delay) {
        requireNonNull(entity, "Entity cannot be null");
        requireNonNull(task, "Task cannot be null");
        ScheduledTask scheduled = entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, Math.max(1, Ticks.of(delay)));
        return scheduled != null ? handle(scheduled) : RETIRED;
    }

    @Override
    @NotNull
    public TaskHandle later(@NotNull Location location, @NotNull Runnable task, @NotNull Duration delay) {
        requireNonNull(location, "Location cannot be null");
        requireNonNull(task, "Task cannot be null");
        return handle(Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduled -> task.run(), Math.max(1, Ticks.of(delay))));
    }

    @Override
    @NotNull
    public TaskHandle repeating(@NotNull Entity entity, @NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        requireNonNull(entity, "Entity cannot be null");
        requireNonNull(task, "Task cannot be null");
        ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(plugin, ignored -> task.run(), null,
                Math.max(1, Ticks.of(delay)), Math.max(1, Ticks.of(period)));
        return scheduled != null ? handle(scheduled) : RETIRED;
    }

    @Override
    @NotNull
    public TaskHandle repeating(@NotNull Location location, @NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        requireNonNull(location, "Location cannot be null");
        requireNonNull(task, "Task cannot be null");
        return handle(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, scheduled -> task.run(),
                Math.max(1, Ticks.of(delay)), Math.max(1, Ticks.of(period))));
    }

    @Override
    public boolean isSyncThread() {
        if (IS_GLOBAL_TICK_THREAD == null) {
            return Bukkit.isPrimaryThread();
        }
        try {
            return (boolean) IS_GLOBAL_TICK_THREAD.invoke(Bukkit.getServer());
        } catch (Throwable t) {
            return Bukkit.isPrimaryThread();
        }
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(requireNonNull(entity, "Entity cannot be null"));
    }

    @Override
    public boolean isOwnedByCurrentThread(@NotNull Location location) {
        return Bukkit.isOwnedByCurrentRegion(requireNonNull(location, "Location cannot be null"));
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private static long millis(Duration duration) {
        requireNonNull(duration, "Duration cannot be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration cannot be negative: " + duration);
        }
        return duration.toMillis();
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
