package org.bxteam.quark.config.backend;

import org.bxteam.quark.config.ConfigException;

/**
 * A {@link Backend} is not on the class path and could not be downloaded.
 */
public class BackendUnavailableException extends ConfigException {
    /**
     * @param message the detail message, with a hint how to fix it
     */
    public BackendUnavailableException(String message) {
        super(message);
    }

    /**
     * @param message the detail message, with a hint how to fix it
     * @param cause the cause
     */
    public BackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
