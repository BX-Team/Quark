package org.bxteam.quark.classloader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.util.Objects.requireNonNull;

/**
 * Finds JARs added to a plugin class path whose classes are still served from somewhere else.
 *
 * <p>Plugin class loaders ask their parent (the server) first. A library the server already ships in the same
 * package, such as Gson or SnakeYAML, therefore wins over the version the plugin downloaded, silently. Relocating the
 * library avoids that.</p>
 */
public final class ShadowedDependencyDetector {
    private ShadowedDependencyDetector() {
    }

    /**
     * A class of a JAR that the class loader finds elsewhere.
     *
     * @param className the class looked up
     * @param location where the class loader finds it instead
     */
    public record Shadowing(@NotNull String className, @NotNull String location) {
    }

    /**
     * Checks one class of the JAR: if the class loader finds it outside of the JAR, the JAR is shadowed.
     *
     * @param jar a JAR that was added to the class path of {@code classLoader}
     * @param classLoader the class loader of the plugin
     * @return where the class is found instead, or null if the JAR is used (or nothing can be checked)
     */
    @Nullable
    public static Shadowing find(@NotNull Path jar, @NotNull ClassLoader classLoader) {
        requireNonNull(jar, "JAR cannot be null");
        requireNonNull(classLoader, "Class loader cannot be null");

        String resource = sampleClass(jar);
        if (resource == null) {
            return null;
        }
        URL url = classLoader.getResource(resource);
        if (url == null) {
            return null;
        }
        Path source = jarOf(url);
        if (source != null && sameFile(source, jar)) {
            return null;
        }
        String className = resource.substring(0, resource.length() - ".class".length()).replace('/', '.');
        return new Shadowing(className, source != null ? source.toString() : url.toString());
    }

    /**
     * @return the path of the first regular class of the JAR, or null if it has none
     */
    @Nullable
    static String sampleClass(Path jar) {
        try (JarFile file = new JarFile(jar.toFile(), false)) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.endsWith("module-info.class")
                        && !name.endsWith("package-info.class") && name.indexOf('/') > 0) {
                    return name;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    @Nullable
    private static Path jarOf(URL url) {
        try {
            if ("jar".equals(url.getProtocol())) {
                URL jarFile = ((JarURLConnection) url.openConnection()).getJarFileURL();
                return "file".equals(jarFile.getProtocol()) ? Path.of(jarFile.toURI()) : null;
            }
            if ("file".equals(url.getProtocol())) {
                return Path.of(url.toURI());
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static boolean sameFile(Path a, Path b) {
        try {
            return a.toRealPath().equals(b.toRealPath());
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }
}
