package org.bxteam.quark.velocity;

import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
public final class VelocityUpdateNotifier {
    private final ProxyServer server;
    private final Object plugin;
    private final EventHandler<PostLoginEvent> handler;

    private VelocityUpdateNotifier(ProxyServer server, Object plugin, EventHandler<PostLoginEvent> handler) {
        this.server = server;
        this.plugin = plugin;
        this.handler = handler;
    }

    /**
     * Registers the notifier with the default message {@code "[plugin-id] A new version ... is available ..."}.
     *
     * @param server the proxy server
     * @param plugin the plugin instance
     * @param checker the update checker
     * @param permission the permission a player needs to be notified
     * @return the registered notifier, see {@link #unregister()}
     */
    @NotNull
    public static VelocityUpdateNotifier register(@NotNull ProxyServer server, @NotNull Object plugin,
                                                  @NotNull UpdateChecker checker, @NotNull String permission) {
        requireNonNull(server, "Server cannot be null");
        requireNonNull(plugin, "Plugin cannot be null");
        String name = server.getPluginManager().fromInstance(plugin)
                .map(container -> container.getDescription().getName().orElse(container.getDescription().getId()))
                .orElse(plugin.getClass().getSimpleName());
        return register(server, plugin, checker, permission, outdated -> Component.text("[" + name + "] ", NamedTextColor.YELLOW)
                .append(Component.text(outdated.describe(), NamedTextColor.WHITE)));
    }

    /**
     * Registers the notifier.
     *
     * @param server the proxy server
     * @param plugin the plugin instance
     * @param checker the update checker
     * @param permission the permission a player needs to be notified
     * @param message builds the chat message
     * @return the registered notifier, see {@link #unregister()}
     */
    @NotNull
    public static VelocityUpdateNotifier register(@NotNull ProxyServer server, @NotNull Object plugin, @NotNull UpdateChecker checker,
                                                  @NotNull String permission, @NotNull Function<UpdateStatus.Outdated, Component> message) {
        requireNonNull(server, "Server cannot be null");
        requireNonNull(plugin, "Plugin cannot be null");
        requireNonNull(checker, "Checker cannot be null");
        requireNonNull(permission, "Permission cannot be null");
        requireNonNull(message, "Message cannot be null");

        EventHandler<PostLoginEvent> handler = event -> {
            if (checker.lastStatus().orElse(null) instanceof UpdateStatus.Outdated outdated && event.getPlayer().hasPermission(permission)) {
                event.getPlayer().sendMessage(message.apply(outdated));
            }
        };
        server.getEventManager().register(plugin, PostLoginEvent.class, handler);
        return new VelocityUpdateNotifier(server, plugin, handler);
    }

    /**
     * Stops notifying players.
     */
    public void unregister() {
        server.getEventManager().unregister(plugin, handler);
    }
}
