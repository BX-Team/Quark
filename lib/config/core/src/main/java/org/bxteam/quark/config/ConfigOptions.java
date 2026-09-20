package org.bxteam.quark.config;

import org.bxteam.quark.common.JulLogger;
import org.bxteam.quark.common.QuarkLogger;
import org.bxteam.quark.config.serdes.SerdesPack;
import org.bxteam.quark.config.validation.ConfigValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Options of a {@link QuarkConfig}, set in the configurer of {@link QuarkConfig#create(Class, java.util.function.Consumer)}.
 *
 * <p>Only {@link #file(Path)} is required. The format, {@link SerdesPack}s and {@link ConfigValidator}s of the
 * modules on the class path ({@code quark-config-yaml}, {@code quark-config-serdes-bukkit},
 * {@code quark-config-validator}) are found with {@link java.util.ServiceLoader} unless {@link #discover(boolean)}
 * turns that off.</p>
 */
public final class ConfigOptions {
    private Path file;
    private ConfigFormat format;
    private final List<SerdesPack> serdes = new ArrayList<>();
    private final List<Migration> migrations = new ArrayList<>();
    private final List<ConfigValidator> validators = new ArrayList<>();
    private boolean removeOrphans;
    private boolean discover = true;
    private QuarkLogger logger;

    ConfigOptions() {
    }

    /**
     * @param file the configuration file
     * @return these options
     */
    @NotNull
    public ConfigOptions file(@NotNull Path file) {
        this.file = requireNonNull(file, "File cannot be null").toAbsolutePath();
        return this;
    }

    /**
     * @param file the configuration file
     * @return these options
     */
    @NotNull
    public ConfigOptions file(@NotNull File file) {
        return file(requireNonNull(file, "File cannot be null").toPath());
    }

    /**
     * @param format the file format, found with {@link java.util.ServiceLoader} by default
     * @return these options
     */
    @NotNull
    public ConfigOptions format(@NotNull ConfigFormat format) {
        this.format = requireNonNull(format, "Format cannot be null");
        return this;
    }

    /**
     * Adds serializers. Packs added here win over discovered ones and over the standard serializers.
     *
     * <pre>{@code
     * options.serdes(registry -> registry.register(Rank.class, new RankSerializer()));
     * }</pre>
     *
     * @param packs the packs
     * @return these options
     */
    @NotNull
    public ConfigOptions serdes(@NotNull SerdesPack... packs) {
        for (SerdesPack pack : requireNonNull(packs, "Packs cannot be null")) {
            serdes.add(requireNonNull(pack, "Pack cannot be null"));
        }
        return this;
    }

    /**
     * Adds migration steps for files with an older {@link org.bxteam.quark.config.annotation.ConfigVersion}.
     *
     * @param steps the steps
     * @return these options
     */
    @NotNull
    public ConfigOptions migrations(@NotNull Migration... steps) {
        for (Migration step : requireNonNull(steps, "Migrations cannot be null")) {
            migrations.add(requireNonNull(step, "Migration cannot be null"));
        }
        return this;
    }

    /**
     * Adds validators, run in addition to discovered ones.
     *
     * @param validators the validators
     * @return these options
     */
    @NotNull
    public ConfigOptions validator(@NotNull ConfigValidator... validators) {
        for (ConfigValidator validator : requireNonNull(validators, "Validators cannot be null")) {
            this.validators.add(requireNonNull(validator, "Validator cannot be null"));
        }
        return this;
    }

    /**
     * Removes keys from the file that no field is bound to. By default they are kept at the end of their section.
     *
     * @return these options
     */
    @NotNull
    public ConfigOptions removeOrphans() {
        return removeOrphans(true);
    }

    /**
     * @param remove whether keys no field is bound to are removed from the file
     * @return these options
     */
    @NotNull
    public ConfigOptions removeOrphans(boolean remove) {
        this.removeOrphans = remove;
        return this;
    }

    /**
     * @param discover whether the format, serializers and validators of modules on the class path are found
     *                 with {@link java.util.ServiceLoader}, true by default
     * @return these options
     */
    @NotNull
    public ConfigOptions discover(boolean discover) {
        this.discover = discover;
        return this;
    }

    /**
     * @param logger reports migrations and skipped modules, a {@link JulLogger} named {@code Quark} by default
     * @return these options
     */
    @NotNull
    public ConfigOptions logger(@NotNull QuarkLogger logger) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
        return this;
    }

    @Nullable
    Path file() {
        return file;
    }

    @Nullable
    ConfigFormat format() {
        return format;
    }

    List<SerdesPack> serdes() {
        return Collections.unmodifiableList(serdes);
    }

    List<Migration> migrations() {
        return Collections.unmodifiableList(migrations);
    }

    List<ConfigValidator> validators() {
        return Collections.unmodifiableList(validators);
    }

    boolean removeOrphansEnabled() {
        return removeOrphans;
    }

    boolean discoverEnabled() {
        return discover;
    }

    QuarkLogger logger() {
        return logger != null ? logger : JulLogger.of("Quark");
    }
}
