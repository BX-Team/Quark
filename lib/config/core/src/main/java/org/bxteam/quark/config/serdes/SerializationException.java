package org.bxteam.quark.config.serdes;

import org.bxteam.quark.config.ConfigException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A value could not be converted to or from a {@link org.bxteam.quark.config.ConfigNode}. The
 * {@link SerdesContext} adds the path of the node to the message.
 */
public class SerializationException extends ConfigException {
    /** The path of the failing node, set once by the context. */
    private String path;

    /**
     * @param message what is wrong
     */
    public SerializationException(@NotNull String message) {
        super(message);
    }

    /**
     * @param message what is wrong
     * @param cause the cause
     */
    public SerializationException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /**
     * @return the path of the failing node, or null if not known yet
     */
    @Nullable
    public String path() {
        return path;
    }

    void initPath(String path) {
        if (this.path == null) {
            this.path = path;
        }
    }

    @Override
    public String getMessage() {
        return path != null ? path + ": " + super.getMessage() : super.getMessage();
    }
}
