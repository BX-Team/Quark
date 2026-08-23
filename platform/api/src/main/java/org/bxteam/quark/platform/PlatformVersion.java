package org.bxteam.quark.platform;

import org.bxteam.quark.common.SemanticVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

import static java.util.Objects.requireNonNull;

/**
 * Version of the running server.
 *
 * @param name the server software name, e.g. {@code "Paper"} or {@code "Velocity"}
 * @param server the server software version
 * @param minecraft the Minecraft version, or null on proxies that support a range of versions
 */
public record PlatformVersion(@NotNull String name,
                              @NotNull SemanticVersion server,
                              @Nullable SemanticVersion minecraft) implements Comparable<PlatformVersion> {
    private static final Comparator<PlatformVersion> ORDER = Comparator
            .comparing(PlatformVersion::minecraft, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(PlatformVersion::server);

    /**
     * @throws NullPointerException if name or server is null
     */
    public PlatformVersion {
        requireNonNull(name, "Name cannot be null");
        requireNonNull(server, "Server version cannot be null");
    }

    /**
     * @param version a Minecraft version such as {@code "1.20.4"}
     * @return true if the Minecraft version is known and at least {@code version}
     */
    public boolean isMinecraftAtLeast(@NotNull String version) {
        return minecraft != null && minecraft.isAtLeast(SemanticVersion.parse(version));
    }

    /**
     * @param version a server software version
     * @return true if the server software version is at least {@code version}
     */
    public boolean isServerAtLeast(@NotNull String version) {
        return server.isAtLeast(SemanticVersion.parse(version));
    }

    /**
     * Orders by Minecraft version (unknown first), then by server version.
     */
    @Override
    public int compareTo(@NotNull PlatformVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return name + " " + server + (minecraft != null ? " (MC " + minecraft + ")" : "");
    }
}
