package org.bxteam.quark.config;

import org.bxteam.quark.common.QuarkLogger;
import org.bxteam.quark.config.annotation.ConfigVersion;
import org.bxteam.quark.config.annotation.Exclude;
import org.bxteam.quark.config.annotation.Header;
import org.bxteam.quark.config.serdes.SerdesContext;
import org.bxteam.quark.config.serdes.SerdesPack;
import org.bxteam.quark.config.serdes.SerdesRegistry;
import org.bxteam.quark.config.serdes.SerializationException;
import org.bxteam.quark.config.validation.ConfigValidationException;
import org.bxteam.quark.config.validation.ConfigValidator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Base class of configuration objects. Public fields (any non-static, non-transient field without
 * {@link Exclude}) are bound to keys in field order; field initializers are the defaults.
 *
 * <pre>{@code
 * @Header("MyPlugin configuration")
 * @NameStrategy(NameStyle.HYPHEN_CASE)
 * public class PluginConfig extends QuarkConfig {
 *     @Comment("Prefix of all messages")
 *     public String prefix = "<gray>[MyPlugin]</gray> ";
 *
 *     public Database database = new Database();
 *
 *     public static class Database {
 *         public String host = "localhost";
 *         public int port = 3306;
 *     }
 * }
 *
 * PluginConfig config = QuarkConfig.create(PluginConfig.class, options -> options
 *         .file(dataDirectory.resolve("config.yml")));
 * config.load();
 * }</pre>
 *
 * <p>{@link #load()} works okaeri-style: the file is generated from the class again on every save, in field order,
 * with comments from {@link org.bxteam.quark.config.annotation.Comment} and {@link Header}, keeping the values of
 * the file. Missing keys are added with their defaults; comments written by hand are not kept. Keys no field is
 * bound to are kept at the end of their section unless {@link ConfigOptions#removeOrphans()} is set.</p>
 *
 * <p>Loading is all or nothing: the file is read into a fresh instance, migrated and validated, and only then are
 * the values copied into this object. If anything fails, this object keeps its values and the file is left
 * untouched. Files are written to a temporary file first and then moved over the old one.</p>
 *
 * <p>The class needs a no-argument constructor (it may be private).</p>
 */
public abstract class QuarkConfig {
    private transient ConfigOptions options;
    private transient ConfigFormat format;
    private transient SerdesRegistry registry;
    private transient List<ConfigValidator> validators;
    private transient QuarkLogger logger;
    private transient IdentityHashMap<Object, Map<String, ConfigNode>> orphans = new IdentityHashMap<>();

    /**
     * Creates a configuration object. Nothing is read until {@link #load()}.
     *
     * @param type the configuration class
     * @param configurer sets the options, at least {@link ConfigOptions#file(Path)}
     * @param <T> the configuration type
     * @return the configuration with its default values
     * @throws ConfigException if the class cannot be created, no file is set or no format is available
     */
    @NotNull
    public static <T extends QuarkConfig> T create(@NotNull Class<T> type, @NotNull Consumer<ConfigOptions> configurer) {
        requireNonNull(type, "Type cannot be null");
        requireNonNull(configurer, "Configurer cannot be null");
        ConfigOptions options = new ConfigOptions();
        configurer.accept(options);
        if (options.file() == null) {
            throw new ConfigException("No file set for " + type.getName() + ", call options.file(...)");
        }
        T config = instantiate(type);
        ((QuarkConfig) config).init(options);
        return config;
    }

    /**
     * Creates a configuration object for a file with default options.
     *
     * @param type the configuration class
     * @param file the file
     * @param <T> the configuration type
     * @return the configuration with its default values
     */
    @NotNull
    public static <T extends QuarkConfig> T create(@NotNull Class<T> type, @NotNull Path file) {
        requireNonNull(file, "File cannot be null");
        return create(type, options -> options.file(file));
    }

    private void init(ConfigOptions options) {
        this.options = options;
        this.logger = options.logger();
        this.format = options.format() != null ? options.format() : discoverFormat();
        this.registry = SerdesRegistry.create();
        this.validators = new ArrayList<>();

        if (options.discoverEnabled()) {
            for (SerdesPack pack : discover(SerdesPack.class)) {
                try {
                    registry.register(pack);
                } catch (LinkageError e) {
                    // e.g. the Bukkit serializers on a proxy: the platform classes are missing
                    logger.debug(() -> "Skipped serializers " + pack.getClass().getName() + ": " + e);
                }
            }
            validators.addAll(discover(ConfigValidator.class));
        }
        options.serdes().forEach(registry::register);
        validators.addAll(options.validators());

        ConfigVersion version = getClass().getAnnotation(ConfigVersion.class);
        if (version != null && version.value() < 1) {
            throw new ConfigException("@ConfigVersion of " + getClass().getName() + " must be at least 1");
        }
        if (version == null && !options.migrations().isEmpty()) {
            throw new ConfigException(getClass().getName() + " has migrations but no @ConfigVersion");
        }
    }

    /**
     * Loads the file, creating it with the defaults if it does not exist: reads it, applies the migrations,
     * binds and validates the values, copies them into this object and writes the file again if it changed.
     *
     * @throws ConfigValidationException if values failed validation; this object and the file are unchanged
     * @throws ConfigException if the file cannot be read, migrated, converted or written
     */
    public synchronized void load() {
        checkCreated();
        Path file = options.file();
        Path directory = file.getParent();
        format.prepare(directory != null ? directory : file.toAbsolutePath().getParent());

        String original = null;
        ConfigNode tree;
        if (Files.exists(file)) {
            try {
                original = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ConfigException("Cannot read " + file + ": " + e.getMessage(), e);
            }
            try {
                tree = format.read(original);
            } catch (ConfigException e) {
                throw new ConfigException("Cannot parse " + file + ": " + e.getMessage(), e);
            }
        } else {
            tree = ConfigNode.root();
        }

        migrate(tree, original != null);

        QuarkConfig fresh = instantiate(getClass());
        IdentityHashMap<Object, Map<String, ConfigNode>> loadedOrphans = new IdentityHashMap<>();
        SerdesContext context = new SerdesContext(registry, validators, options.removeOrphansEnabled() ? null : loadedOrphans);
        try {
            context.deserializeInto(tree, fresh);
        } catch (SerializationException e) {
            throw new ConfigException("Cannot load " + file + ": " + e.getMessage(), e);
        }
        if (!context.violations().isEmpty()) {
            throw new ConfigValidationException(file.toString(), context.violations());
        }

        String text = render(fresh, loadedOrphans);

        copyFields(fresh, this);
        Map<String, ConfigNode> rootOrphans = loadedOrphans.remove(fresh);
        if (rootOrphans != null) {
            loadedOrphans.put(this, rootOrphans);
        }
        this.orphans = loadedOrphans;

        if (!text.equals(original)) {
            write(file, text);
        }
    }

    /**
     * Loads the file again, see {@link #load()}. If the file is invalid, the current values stay in place.
     *
     * @throws ConfigValidationException if values failed validation
     * @throws ConfigException if the file cannot be read, migrated, converted or written
     */
    public synchronized void reload() {
        load();
    }

    /**
     * Writes the current values to the file.
     *
     * @throws ConfigException if a value cannot be converted or the file cannot be written
     */
    public synchronized void save() {
        checkCreated();
        Path file = options.file();
        Path directory = file.getParent();
        format.prepare(directory != null ? directory : file.toAbsolutePath().getParent());
        write(file, render(this, orphans));
    }

    /**
     * @return the configuration file
     */
    @NotNull
    public Path file() {
        checkCreated();
        return options.file();
    }

    /**
     * Converts the current values to a tree, as they would be saved.
     *
     * @return the root node, with the header as its comment
     */
    @NotNull
    public synchronized ConfigNode toNode() {
        checkCreated();
        return tree(this, orphans);
    }

    private void migrate(ConfigNode tree, boolean fromFile) {
        ConfigVersion version = getClass().getAnnotation(ConfigVersion.class);
        if (version == null) {
            return;
        }
        ConfigNode versionNode = tree.node(version.key());
        int current;
        if (!fromFile) {
            current = version.value();
        } else if (versionNode.isVirtual()) {
            current = 1;
        } else {
            try {
                current = Integer.parseInt(versionNode.getString("").trim());
            } catch (NumberFormatException e) {
                throw new ConfigException("Invalid " + version.key() + " '" + versionNode.getString() + "' in " + options.file());
            }
        }
        versionNode.remove();

        int target = version.value();
        if (current > target) {
            throw new ConfigException(options.file() + " has version " + current + ", but this version of the plugin only supports up to "
                    + target + ". Update the plugin or restore a backup of the file.");
        }
        while (current < target) {
            int from = current;
            Migration step = options.migrations().stream()
                    .filter(migration -> migration.from() == from)
                    .findFirst()
                    .orElseThrow(() -> new ConfigException("No migration of " + options.file().getFileName() + " from version " + from
                            + " (target " + target + ")"));
            if (step.to() > target) {
                throw new ConfigException("Migration " + step.from() + " -> " + step.to() + " goes past version " + target);
            }
            try {
                step.migrate(tree);
            } catch (RuntimeException e) {
                throw new ConfigException("Migration of " + options.file().getFileName() + " from version " + step.from()
                        + " to " + step.to() + " failed: " + e.getMessage(), e);
            }
            logger.info("Migrated {} from version {} to {}", options.file().getFileName(), step.from(), step.to());
            current = step.to();
        }
    }

    private String render(QuarkConfig source, IdentityHashMap<Object, Map<String, ConfigNode>> sourceOrphans) {
        return format.write(tree(source, sourceOrphans));
    }

    private ConfigNode tree(QuarkConfig source, IdentityHashMap<Object, Map<String, ConfigNode>> sourceOrphans) {
        ConfigNode root = ConfigNode.root();
        ConfigVersion version = getClass().getAnnotation(ConfigVersion.class);
        if (version != null) {
            root.node(version.key()).set(version.value());
        }
        SerdesContext context = new SerdesContext(registry, List.of(), options.removeOrphansEnabled() ? null : sourceOrphans);
        try {
            context.serializeInto(source, root);
        } catch (SerializationException e) {
            throw new ConfigException("Cannot save " + options.file() + ": " + e.getMessage(), e);
        }
        Header header = getClass().getAnnotation(Header.class);
        if (header != null) {
            root.comment(header.value());
        }
        return root;
    }

    private static void write(Path file, String text) {
        try {
            Path directory = file.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ConfigException("Cannot write " + file + ": " + e.getMessage(), e);
        }
    }

    private static void copyFields(QuarkConfig from, QuarkConfig to) {
        for (Class<?> type = from.getClass(); type != QuarkConfig.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()
                        || field.isAnnotationPresent(Exclude.class)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    field.set(to, field.get(from));
                } catch (IllegalAccessException | RuntimeException e) {
                    throw new ConfigException("Cannot update " + type.getName() + "#" + field.getName(), e);
                }
            }
        }
    }

    private static <T extends QuarkConfig> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new ConfigException(type.getName() + " needs a no-argument constructor");
        } catch (InvocationTargetException e) {
            throw new ConfigException("Cannot create " + type.getName() + ": " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ConfigException("Cannot create " + type.getName() + ": " + e, e);
        }
    }

    private void checkCreated() {
        if (options == null) {
            throw new IllegalStateException(getClass().getName() + " was not created with QuarkConfig.create(...)");
        }
    }

    private static ConfigFormat discoverFormat() {
        List<ConfigFormat> formats = discover(ConfigFormat.class);
        if (formats.isEmpty()) {
            throw new ConfigException("No configuration format found. Add quark-config-yaml "
                    + "(quark { modules(QuarkModule.CONFIG) }) or set one with options.format(...)");
        }
        return formats.get(0);
    }

    private static <S> List<S> discover(Class<S> service) {
        List<S> found = new ArrayList<>();
        Iterator<S> iterator = ServiceLoader.load(service, QuarkConfig.class.getClassLoader()).iterator();
        while (true) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                found.add(iterator.next());
            } catch (ServiceConfigurationError | LinkageError e) {
                // a provider whose dependencies are missing on this platform
            }
        }
        return found;
    }
}
