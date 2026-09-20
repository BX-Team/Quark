package org.bxteam.quark.config.validation;

import org.bxteam.quark.config.ConfigException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Values of a configuration file failed validation. The message lists every {@link Violation}.
 */
public class ConfigValidationException extends ConfigException {
    /** The violations, in the order they were found. */
    private final List<Violation> violations;

    /**
     * @param source the file or configuration that failed, used in the message
     * @param violations the violations, not empty
     */
    public ConfigValidationException(@NotNull String source, @NotNull List<Violation> violations) {
        super("Invalid configuration " + source + ":" + violations.stream()
                .map(violation -> "\n  - " + violation)
                .collect(Collectors.joining()));
        this.violations = List.copyOf(violations);
    }

    /**
     * @return the violations
     */
    @NotNull
    @Unmodifiable
    public List<Violation> violations() {
        return violations;
    }
}
