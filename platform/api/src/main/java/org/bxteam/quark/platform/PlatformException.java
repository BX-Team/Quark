package org.bxteam.quark.platform;

/**
 * Thrown when a platform cannot be detected or created.
 */
public class PlatformException extends RuntimeException {
    /**
     * @param message the detail message
     */
    public PlatformException(String message) {
        super(message);
    }

    /**
     * @param message the detail message
     * @param cause the cause
     */
    public PlatformException(String message, Throwable cause) {
        super(message, cause);
    }
}
