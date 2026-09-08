package org.bxteam.quark.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bxteam.quark.update.UpdateChecker;
import org.bxteam.quark.update.UpdateStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Tells players with a permission about a new version when they join. Requires {@code quark-update}.
 *
 * <pre>{@code
 * UpdateChecker checker = new UpdateChecker(UpdateProvider.modrinth("my-plugin"), getDescription().getVersion());
 * checker.checkAndLog(platform.logger());
 * BukkitUpdateNotifier.register(this, checker, "myplugin.update");
 * }</pre>
 *
 * <p>The notifier uses the result of the last finished check and never checks by itself.
 * Works on Bukkit, Paper and Folia.</p>
 */
public final class BukkitUpdateNotifier implements Listener {
    private final UpdateChecker checker;
    private final String permission;
    private final Function<UpdateStatus.Outdated, String> message;

    private BukkitUpdateNotifier(UpdateChecker checker, String permission, Function<UpdateStatus.Outdated, String> message) {
        this.checker = checker;
        this.permission = permission;
        this.message = message;
    }

    /**
     * Registers the notifier with the default message {@code "[Plugin] A new version ... is available ..."}.
     *
     * @param plugin the plugin
     * @param checker the update checker
     * @param permission the permission a player needs to be notified
     * @return the registered listener, see {@link #unregister()}
     */
    @NotNull
    public static BukkitUpdateNotifier register(@NotNull Plugin plugin, @NotNull UpdateChecker checker, @NotNull String permission) {
        requireNonNull(plugin, "Plugin cannot be null");
        String prefix = "§e[" + plugin.getName() + "] §f";
        return register(plugin, checker, permission, outdated -> prefix + outdated.describe());
    }

    /**
     * Registers the notifier.
     *
     * @param plugin the plugin
     * @param checker the update checker
     * @param permission the permission a player needs to be notified
     * @param message builds the chat message, legacy {@code §} color codes are supported
     * @return the registered listener, see {@link #unregister()}
     */
    @NotNull
    public static BukkitUpdateNotifier register(@NotNull Plugin plugin, @NotNull UpdateChecker checker, @NotNull String permission,
                                                @NotNull Function<UpdateStatus.Outdated, String> message) {
        requireNonNull(plugin, "Plugin cannot be null");
        BukkitUpdateNotifier notifier = new BukkitUpdateNotifier(
                requireNonNull(checker, "Checker cannot be null"),
                requireNonNull(permission, "Permission cannot be null"),
                requireNonNull(message, "Message cannot be null"));
        Bukkit.getPluginManager().registerEvents(notifier, plugin);
        return notifier;
    }

    /**
     * Stops notifying players.
     */
    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (checker.lastStatus().orElse(null) instanceof UpdateStatus.Outdated outdated && event.getPlayer().hasPermission(permission)) {
            event.getPlayer().sendMessage(message.apply(outdated));
        }
    }
}
