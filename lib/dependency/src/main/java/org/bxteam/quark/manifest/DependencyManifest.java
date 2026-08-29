package org.bxteam.quark.manifest;

import org.bxteam.quark.ResourceProvider;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.relocation.Relocation;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Runtime dependencies, repositories and relocations declared at build time.
 *
 * <p>The Quark Gradle plugin writes this manifest to {@value #LOCATION} inside the plugin JAR.
 * The format is line based and UTF-8 encoded:</p>
 * <pre>{@code
 * # comments start with '#'
 * format=1
 *
 * [repositories]
 * https://maven-central.storage-download.googleapis.com/maven2
 *
 * [dependencies]
 * com.google.code.gson:gson:2.11.0
 *
 * [relocations]
 * com.google.gson=my.plugin.libs.gson
 * }</pre>
 *
 * <p>Lines before the first section are {@code key=value} properties; {@code format} is required.
 * Unknown properties and sections are ignored, so newer plugins can add data without breaking older
 * runtimes, while a higher {@code format} value means an incompatible change.</p>
 *
 * @param repositories repository URLs
 * @param dependencies dependencies to load
 * @param relocations relocations to apply
 */
public record DependencyManifest(@NotNull List<String> repositories,
                                 @NotNull List<Dependency> dependencies,
                                 @NotNull List<Relocation> relocations) {
    /** Location of the manifest inside the plugin JAR. */
    public static final String LOCATION = "META-INF/quark/manifest";

    /** Highest manifest format this runtime understands. */
    public static final int FORMAT_VERSION = 1;

    private static final String FORMAT_KEY = "format";
    private static final String REPOSITORIES = "repositories";
    private static final String DEPENDENCIES = "dependencies";
    private static final String RELOCATIONS = "relocations";

    /**
     * @throws NullPointerException if any parameter is null
     */
    public DependencyManifest {
        repositories = List.copyOf(requireNonNull(repositories, "Repositories cannot be null"));
        dependencies = List.copyOf(requireNonNull(dependencies, "Dependencies cannot be null"));
        relocations = List.copyOf(requireNonNull(relocations, "Relocations cannot be null"));
    }

    /**
     * Reads the manifest from {@value #LOCATION}.
     *
     * @param resourceProvider the provider for plugin resources
     * @return the manifest, or empty if the plugin has none
     * @throws ManifestException if the manifest exists but cannot be parsed
     */
    @NotNull
    public static Optional<DependencyManifest> load(@NotNull ResourceProvider resourceProvider) {
        requireNonNull(resourceProvider, "Resource provider cannot be null");

        InputStream stream = resourceProvider.getResourceAsStream(LOCATION);
        if (stream == null) {
            return Optional.empty();
        }

        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return Optional.of(parse(reader));
        } catch (IOException e) {
            throw new ManifestException("Failed to read " + LOCATION, e);
        }
    }

    /**
     * Parses a manifest.
     *
     * @param content the manifest text
     * @return the manifest
     * @throws ManifestException if the manifest is malformed
     */
    @NotNull
    public static DependencyManifest parse(@NotNull String content) {
        try {
            return parse(new StringReader(requireNonNull(content, "Content cannot be null")));
        } catch (IOException e) {
            throw new ManifestException("Failed to read manifest", e);
        }
    }

    private static DependencyManifest parse(Reader source) throws IOException {
        List<String> repositories = new ArrayList<>();
        List<Dependency> dependencies = new ArrayList<>();
        List<Relocation> relocations = new ArrayList<>();
        Integer format = null;
        String section = null;
        int lineNumber = 0;

        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                if (format == null) {
                    throw new ManifestException("Line " + lineNumber + ": '" + FORMAT_KEY + "' must be declared before any section");
                }
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }

            if (section == null) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new ManifestException("Line " + lineNumber + ": expected key=value, got '" + line + "'");
                }
                if (line.substring(0, separator).trim().equals(FORMAT_KEY)) {
                    format = parseFormat(line.substring(separator + 1).trim(), lineNumber);
                }
                continue;
            }

            switch (section) {
                case REPOSITORIES -> repositories.add(line);
                case DEPENDENCIES -> dependencies.add(parseDependency(line, lineNumber));
                case RELOCATIONS -> relocations.add(parseRelocation(line, lineNumber));
                default -> {
                    // unknown sections are reserved for newer Quark versions
                }
            }
        }

        if (format == null) {
            throw new ManifestException("Manifest does not declare '" + FORMAT_KEY + "'");
        }

        return new DependencyManifest(repositories, dependencies, relocations);
    }

    private static int parseFormat(String value, int lineNumber) {
        int format;
        try {
            format = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ManifestException("Line " + lineNumber + ": invalid format version '" + value + "'");
        }
        if (format < 1 || format > FORMAT_VERSION) {
            throw new ManifestException("Manifest format " + format + " is not supported (supported: 1.." + FORMAT_VERSION
                    + "). The plugin was built with a newer Quark Gradle plugin than the Quark runtime it contains");
        }
        return format;
    }

    private static Dependency parseDependency(String line, int lineNumber) {
        try {
            return Dependency.fromCoordinates(line);
        } catch (IllegalArgumentException e) {
            throw new ManifestException("Line " + lineNumber + ": invalid dependency '" + line + "'", e);
        }
    }

    private static Relocation parseRelocation(String line, int lineNumber) {
        int separator = line.indexOf('=');
        if (separator <= 0 || separator == line.length() - 1) {
            throw new ManifestException("Line " + lineNumber + ": expected pattern=relocatedPattern, got '" + line + "'");
        }
        return Relocation.of(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
    }

    /**
     * @return true if the manifest declares no dependencies
     */
    public boolean isEmpty() {
        return dependencies.isEmpty();
    }

    /**
     * Serializes the manifest in the current format.
     *
     * @return the manifest text
     */
    @NotNull
    public String write() {
        StringBuilder out = new StringBuilder()
                .append("# Quark dependency manifest\n")
                .append(FORMAT_KEY).append('=').append(FORMAT_VERSION).append('\n');

        out.append("\n[").append(REPOSITORIES).append("]\n");
        repositories.forEach(repository -> out.append(repository).append('\n'));

        out.append("\n[").append(DEPENDENCIES).append("]\n");
        dependencies.forEach(dependency -> out.append(dependency.getCoordinates()).append('\n'));

        out.append("\n[").append(RELOCATIONS).append("]\n");
        relocations.forEach(relocation -> out.append(relocation.pattern()).append('=').append(relocation.relocatedPattern()).append('\n'));

        return out.toString();
    }
}
