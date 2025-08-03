package org.bxteam.quark.dependency;

/**
 * Exception thrown when dependency operations fail.
 *
 * <p>This exception is used throughout the dependency management system
 * to indicate various failure conditions such as:</p>
 * <ul>
 *   <li>Dependency download failures</li>
 *   <li>Version resolution conflicts</li>
 *   <li>Repository access issues</li>
 *   <li>Dependency parsing errors</li>
 * </ul>
 */
public class DependencyException extends RuntimeException {
    /**
     * Creates a new dependency exception with no message or cause.
     */
    public DependencyException() {
        super();
    }

    /**
     * Creates a new dependency exception with the specified message.
     *
     * @param message the detail message
     */
    public DependencyException(String message) {
        super(message);
    }

    /**
     * Creates a new dependency exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public DependencyException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new dependency exception with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public DependencyException(Throwable cause) {
        super(cause);
    }
}
