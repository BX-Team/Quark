package org.bxteam.quark.update;

import org.bxteam.quark.common.SemanticVersion;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * Result of an update check.
 */
public sealed interface UpdateStatus {
    /**
     * The running version is the newest one, or no release is published.
     *
     * @param current the running version
     */
    record UpToDate(@NotNull SemanticVersion current) implements UpdateStatus {
        /**
         * @throws NullPointerException if current is null
         */
        public UpToDate {
            requireNonNull(current, "Current version cannot be null");
        }
    }

    /**
     * A newer release is available.
     *
     * @param current the running version
     * @param latest the newest release
     */
    record Outdated(@NotNull SemanticVersion current, @NotNull ReleaseInfo latest) implements UpdateStatus {
        /**
         * @throws NullPointerException if any parameter is null
         */
        public Outdated {
            requireNonNull(current, "Current version cannot be null");
            requireNonNull(latest, "Latest release cannot be null");
        }

        /**
         * @return a one-line description such as {@code "A new version 1.2.0 is available (running 1.1.0): https://..."}
         */
        @NotNull
        public String describe() {
            return "A new version " + latest.version() + " is available (running " + current + ")"
                    + (latest.url() != null ? ": " + latest.url() : "");
        }
    }

    /**
     * The check failed, e.g. the source was unreachable.
     *
     * @param cause the failure
     */
    record Unknown(@NotNull Throwable cause) implements UpdateStatus {
        /**
         * @throws NullPointerException if cause is null
         */
        public Unknown {
            requireNonNull(cause, "Cause cannot be null");
        }
    }
}
