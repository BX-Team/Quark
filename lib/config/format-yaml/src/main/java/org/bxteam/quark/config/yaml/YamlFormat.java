package org.bxteam.quark.config.yaml;

import org.bxteam.quark.config.ConfigFormat;
import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.backend.Backend;
import org.bxteam.quark.config.backend.BackendProvider;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * YAML on snakeyaml-engine. Registered with {@link java.util.ServiceLoader}, so configurations use it as soon as this
 * module is on the class path.
 *
 * <p>snakeyaml-engine is not a dependency of this module: {@link BackendProvider} uses it from the class path when
 * the plugin shades it, and otherwise downloads it with {@code quark-dependency} on the first load, relocated to the
 * package the Quark Gradle plugin relocated it to.</p>
 *
 * <p>Comments are written from the tree; comments in the file are not read.</p>
 */
public final class YamlFormat implements ConfigFormat {
    // Shadow rewrites this literal when the Gradle plugin relocates snakeyaml-engine; the suffix and the
    // obfuscated original package are left alone
    private static final String PROBE = "org.snakeyaml.engine.v2.api.Load";
    private static final String PROBE_SUFFIX = ".v2.api.Load";

    static final Backend BACKEND = new Backend(
            "snakeyaml-engine",
            SnakeYamlVersion.COORDINATES,
            "org{}snakeyaml{}engine".replace("{}", "."),
            PROBE.substring(0, PROBE.length() - PROBE_SUFFIX.length()),
            PROBE
    );

    private volatile SnakeYamlBackend backend;

    /**
     * Creates the format. Used by {@link java.util.ServiceLoader}; prefer {@link #create()}.
     */
    public YamlFormat() {
    }

    /**
     * @return a new YAML format
     */
    @NotNull
    public static YamlFormat create() {
        return new YamlFormat();
    }

    @Override
    @NotNull
    public String name() {
        return "YAML";
    }

    @Override
    public void prepare(@NotNull Path dataDirectory) {
        requireNonNull(dataDirectory, "Data directory cannot be null");
        if (backend == null) {
            synchronized (this) {
                if (backend == null) {
                    BackendProvider.ensure(BACKEND, dataDirectory);
                    backend = new SnakeYamlBackend();
                }
            }
        }
    }

    @Override
    @NotNull
    public ConfigNode read(@NotNull String content) {
        return backend().read(requireNonNull(content, "Content cannot be null"));
    }

    @Override
    @NotNull
    public String write(@NotNull ConfigNode root) {
        return backend().write(requireNonNull(root, "Root cannot be null"));
    }

    private SnakeYamlBackend backend() {
        if (backend == null) {
            // used without QuarkConfig: downloaded libraries go to the working directory
            prepare(Path.of("").toAbsolutePath());
        }
        return backend;
    }
}
