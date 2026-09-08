package org.bxteam.quark.bungee;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.bxteam.quark.update.UpdateChecker;
import org.bxteam.quark.update.UpdateStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Tells players with a permission about a new version when they log in. Requires {@code quark-update}.
 *
 * <p>The notifier uses the result of the last finished check and never checks by itself.</p>
 */
public final class BungeeUpdateNotifier implements Listener {
    private final Plugin plugin;
    private final UpdateChecker checker;
    private final String permission;
    private final Function<UpdateStatus.Outdated, String> message;

    private BungeeUpdateNotifier(Plugin plugin, UpdateChecker checker, String permission, Function<UpdateStatus.Outdated, String> message) {
        this.plugin = plugin;
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
    public static BungeeUpdateNotifier register(@NotNull Plugin plugin, @NotNull UpdateChecker checker, @NotNull String permission) {
        requireNonNull(plugin, "Plugin cannot be null");
        String prefix = "§e[" + plugin.getDescription().getName() + "] §f";
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
    public static BungeeUpdateNotifier register(@NotNull Plugin plugin, @NotNull UpdateChecker checker, @NotNull String permission,
                                                @NotNull Function<UpdateStatus.Outdated, String> message) {
        BungeeUpdateNotifier notifier = new BungeeUpdateNotifier(
                requireNonNull(plugin, "Plugin cannot be null"),
                requireNonNull(checker, "Checker cannot be null"),
                requireNonNull(permission, "Permission cannot be null"),
                requireNonNull(message, "Message cannot be null"));
        plugin.getProxy().getPluginManager().registerListener(plugin, notifier);
        return notifier;
    }

    /**
     * Stops notifying players.
     */
    public void unregister() {
        plugin.getProxy().getPluginManager().unregisterListener(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        if (checker.lastStatus().orElse(null) instanceof UpdateStatus.Outdated outdated && event.getPlayer().hasPermission(permission)) {
            event.getPlayer().sendMessage(TextComponent.fromLegacyText(message.apply(outdated)));
        }
    }
}
