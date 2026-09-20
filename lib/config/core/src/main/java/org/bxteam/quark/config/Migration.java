package org.bxteam.quark.config;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Brings a file from one {@link org.bxteam.quark.config.annotation.ConfigVersion} to a newer one by editing
 * its tree before it is bound to the configuration object.
 *
 * <pre>{@code
 * Migration.of(1, 2, root -> root.node("prefix").moveTo(root.node("messages", "prefix")))
 * }</pre>
 */
public interface Migration {
    /**
     * @return the version this step migrates from
     */
    int from();

    /**
     * @return the version this step migrates to, greater than {@link #from()}
     */
    int to();

    /**
     * Edits the tree. The version key is already removed and is set to {@link #to()} afterwards.
     *
     * @param root the root node of the file
     */
    void migrate(@NotNull ConfigNode root);

    /**
     * @param from the version this step migrates from
     * @param to the version this step migrates to
     * @param action edits the tree
     * @return the migration
     * @throws IllegalArgumentException if {@code to <= from} or {@code from < 1}
     */
    @NotNull
    static Migration of(int from, int to, @NotNull Consumer<ConfigNode> action) {
        requireNonNull(action, "Action cannot be null");
        if (from < 1 || to <= from) {
            throw new IllegalArgumentException("A migration must go from a version >= 1 to a newer one, got " + from + " -> " + to);
        }
        return new Migration() {
            @Override
            public int from() {
                return from;
            }

            @Override
            public int to() {
                return to;
            }

            @Override
            public void migrate(@NotNull ConfigNode root) {
                action.accept(root);
            }

            @Override
            public String toString() {
                return "Migration{" + from + " -> " + to + "}";
            }
        };
    }
}
