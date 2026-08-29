package org.bxteam.quark.manifest;

/**
 * Thrown when the dependency manifest cannot be read or is malformed.
 */
public class ManifestException extends RuntimeException {
    /**
     * @param message the detail message
     */
    public ManifestException(String message) {
        super(message);
    }

    /**
     * @param message the detail message
     * @param cause the cause
     */
    public ManifestException(String message, Throwable cause) {
        super(message, cause);
    }
}
