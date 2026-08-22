package org.bxteam.quark.pom.exception;

/**
 * Exception thrown when POM parsing fails.
 */
public class PomParsingException extends Exception {
    /**
     * Creates a new PomParsingException with the specified message.
     *
     * @param message the detail message
     */
    public PomParsingException(String message) {
        super(message);
    }

    /**
     * Creates a new PomParsingException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public PomParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
