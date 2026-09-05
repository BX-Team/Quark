package org.bxteam.quark.update;

/**
 * Thrown (inside the returned future) when an update source answers with an error or unexpected content.
 */
public class UpdateCheckException extends RuntimeException {
    /**
     * @param message the detail message
     */
    public UpdateCheckException(String message) {
        super(message);
    }

    /**
     * @param message the detail message
     * @param cause the cause
     */
    public UpdateCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
