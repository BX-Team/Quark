package org.bxteam.quark.velocity;

import org.bxteam.quark.platform.Platform;
import org.bxteam.quark.platform.PlatformException;
import org.bxteam.quark.platform.PlatformProvider;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Recognizes Velocity plugins for {@link Platform#detect(Object)} and explains how to create the platform:
 * Velocity has no static access to the proxy, so {@link VelocityPlatform#create(com.velocitypowered.api.proxy.ProxyServer, Object, Path)}
 * must be used instead.
 */
public class VelocityPlatformProvider implements PlatformProvider {
    @Override
    public boolean supports(@NotNull Object plugin) {
        return VelocityClasses.isVelocityPlugin(plugin);
    }

    @Override
    @NotNull
    public Platform create(@NotNull Object plugin) {
        throw new PlatformException("The Velocity platform cannot be created from the plugin instance alone. "
                + "Use VelocityPlatform.create(server, plugin, dataDirectory) with the @Inject'ed ProxyServer and @DataDirectory path");
    }
}
