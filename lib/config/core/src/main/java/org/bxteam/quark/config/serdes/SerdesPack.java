package org.bxteam.quark.config.serdes;

import org.jetbrains.annotations.NotNull;

/**
 * A group of serializers registered together, such as the Bukkit types of {@code quark-config-serdes-bukkit}.
 *
 * <p>Packs registered with {@link java.util.ServiceLoader} in
 * {@code META-INF/services/org.bxteam.quark.config.serdes.SerdesPack} are added to every configuration
 * automatically. Such implementations need a public no-argument constructor.</p>
 */
@FunctionalInterface
public interface SerdesPack {
    /**
     * Registers the serializers of this pack.
     *
     * @param registry the registry
     */
    void register(@NotNull SerdesRegistry registry);
}
