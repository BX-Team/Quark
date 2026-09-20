package org.bxteam.quark.config;

/**
 * A configuration could not be loaded or saved.
 */
public class ConfigException extends RuntimeException {
    /**
     * @param message the detail message
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * @param message the detail message
     * @param cause the cause
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
