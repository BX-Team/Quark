package org.bxteam.quark.config;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Reads and writes the {@link ConfigNode} tree of a file. {@code quark-config-yaml} provides the YAML format and
 * registers it with {@link java.util.ServiceLoader}, so {@link ConfigOptions} finds it without configuration.
 */
public interface ConfigFormat {
    /**
     * @return the name of the format, used in error messages
     */
    @NotNull
    String name();

    /**
     * Makes sure the parser behind this format can be used, downloading it if needed (see
     * {@link org.bxteam.quark.config.backend.BackendProvider}). Called before every read and write.
     *
     * @param dataDirectory the directory of the configuration file, used if the parser has to be downloaded
     * @throws ConfigException if the parser is not available
     */
    default void prepare(@NotNull Path dataDirectory) {
    }

    /**
     * Parses a file.
     *
     * @param content the file content
     * @return the root node, an empty map for an empty file
     * @throws ConfigException if the content is malformed
     */
    @NotNull
    ConfigNode read(@NotNull String content);

    /**
     * Writes a tree, with the comment of the root node as the file header and the comments of the other
     * nodes above their keys.
     *
     * @param root the root node
     * @return the file content
     */
    @NotNull
    String write(@NotNull ConfigNode root);
}
