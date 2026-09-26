package org.bxteam.quark.classloader;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.LogLevel;
import org.bxteam.quark.relocation.Relocation;
import org.bxteam.quark.testing.FakeMavenRepository;
import org.bxteam.quark.testing.PomBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowedDependencyTest {
    private static final Dependency LIBRARY = Dependency.of("com.example", "greeter", "1.1");

    @TempDir
    Path temp;

    private FakeMavenRepository repository;
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        repository = new FakeMavenRepository(Files.createDirectory(temp.resolve("remote")));
        repository.publish(LIBRARY, new PomBuilder().build(LIBRARY), FakeMavenRepository.jar(Map.of("com/example/lib/Greeter.class", greeter())));
        publishFromClassPath("org.ow2.asm:asm:9.10.1", ClassWriter.class);
        publishFromClassPath("org.ow2.asm:asm-commons:9.10.1", ClassRemapper.class);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    private LibraryManager manager(URLClassLoader pluginClassLoader) {
        LibraryManager manager = LibraryManager.builder()
                .dataDirectory(temp.resolve("data"))
                .classLoader(pluginClassLoader)
                .logAdapter(new LogAdapter() {
                    @Override
                    public void log(@NotNull LogLevel level, @NotNull String message) {
                        if (level == LogLevel.WARN) {
                            warnings.add(message);
                        }
                    }

                    @Override
                    public void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable throwable) {
                        log(level, message);
                    }
                })
                .build();
        manager.addRepository(repository.url());
        return manager;
    }

    /** A "server" class loader that already ships another version of the library. */
    private URLClassLoader server() throws Exception {
        Path serverJar = temp.resolve("server.jar");
        Files.write(serverJar, FakeMavenRepository.jar(Map.of("com/example/lib/Greeter.class", greeter())));
        return new URLClassLoader(new URL[]{serverJar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    @Test
    void warnsOnceWhenTheServerShadowsALibrary() throws Exception {
        try (URLClassLoader server = server(); URLClassLoader plugin = new URLClassLoader(new URL[0], server)) {
            LibraryManager manager = manager(plugin);
            manager.loadDependency(LIBRARY);
            manager.loadDependency(LIBRARY);
        }

        assertEquals(1, warnings.size(), warnings.toString());
        String warning = warnings.get(0);
        assertTrue(warning.startsWith("com.example:greeter:1.1 is not used: com.example.lib.Greeter is loaded from "), warning);
        assertTrue(warning.contains("server.jar"), warning);
        assertTrue(warning.contains("quark(\"com.example:greeter:1.1\") { relocate = true }"), warning);
        assertTrue(warning.contains("relocate(\"com.example.lib\", ...)"), warning);
    }

    @Test
    void relocatedOrUnshadowedLibrariesAreFine() throws Exception {
        try (URLClassLoader server = server(); URLClassLoader plugin = new URLClassLoader(new URL[0], server)) {
            manager(plugin).loadDependencies(List.of(LIBRARY), List.of(Relocation.of("com.example.lib", "my.plugin.libs.lib")));
        }
        try (URLClassLoader plugin = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
            manager(plugin).loadDependency(LIBRARY);
            assertNotNull(plugin.loadClass("com.example.lib.Greeter"));
        }

        assertEquals(List.of(), warnings);
    }

    @Test
    void detectorComparesWhereTheClassComesFrom() throws Exception {
        Path own = temp.resolve("own.jar");
        Files.write(own, FakeMavenRepository.jar(Map.of("com/example/lib/Greeter.class", greeter())));

        try (URLClassLoader server = server();
             URLClassLoader shadowed = new URLClassLoader(new URL[]{own.toUri().toURL()}, server);
             URLClassLoader alone = new URLClassLoader(new URL[]{own.toUri().toURL()}, null)) {
            ShadowedDependencyDetector.Shadowing shadowing = ShadowedDependencyDetector.find(own, shadowed);
            assertNotNull(shadowing);
            assertEquals("com.example.lib.Greeter", shadowing.className());
            assertTrue(shadowing.location().endsWith("server.jar"), shadowing.location());
            assertNull(ShadowedDependencyDetector.find(own, alone));
        }
    }

    private void publishFromClassPath(String coordinates, Class<?> type) throws Exception {
        Dependency dependency = Dependency.fromCoordinates(coordinates);
        Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        repository.publish(dependency, new PomBuilder().build(dependency), Files.readAllBytes(jar));
    }

    /** {@code public class Greeter {}} */
    private static byte[] greeter() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/lib/Greeter", null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
