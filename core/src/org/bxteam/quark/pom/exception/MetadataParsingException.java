package org.bxteam.quark.pom.exception;

/**
 * Exception thrown when Maven metadata parsing fails.
 *
 * <p>This exception indicates that the metadata XML could not be parsed,
 * either due to XML syntax errors, missing required elements, or I/O issues.</p>
 */
public class MetadataParsingException extends Exception {
    /**
     * Creates a new MetadataParsingException with the specified message.
     *
     * @param message the detail message
     */
    public MetadataParsingException(String message) {
        super(message);
    }

    /**
     * Creates a new MetadataParsingException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public MetadataParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
