package org.bxteam.quark;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

/**
 * Gives access to resources embedded in the plugin JAR.
 */
@FunctionalInterface
public interface ResourceProvider {
    /**
     * Opens a resource.
     *
     * @param resourcePath the path to the resource, without a leading slash
     * @return an input stream for the resource, or null if not found
     */
    @Nullable
    InputStream getResourceAsStream(@NotNull String resourcePath);

    /**
     * @param classLoader the class loader to read resources from
     * @return a provider backed by {@link ClassLoader#getResourceAsStream(String)}
     */
    @NotNull
    static ResourceProvider of(@NotNull ClassLoader classLoader) {
        return classLoader::getResourceAsStream;
    }
}
