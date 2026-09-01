package org.bxteam.quark.relocation;

import me.lucko.jarrelocator.JarRelocator;
import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.testing.FakeMavenRepository;
import org.bxteam.quark.testing.NoopLogAdapter;
import org.bxteam.quark.testing.PomBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RelocationTest {
    private static final Dependency LIBRARY = Dependency.of("com.example", "greeter", "1.0");
    private static final List<Relocation> RELOCATIONS = List.of(Relocation.of("com{}example{}lib", "org.test.relocated.lib"));

    @TempDir
    Path temp;

    private FakeMavenRepository repository;
    private LibraryManager libraryManager;

    @BeforeEach
    void setUp() throws Exception {
        repository = new FakeMavenRepository(Files.createDirectory(temp.resolve("remote")));

        // the relocation tools are downloaded like any other dependency, so serve them from the test class path
        publishFromClassPath("org.ow2.asm:asm:9.7", ClassWriter.class);
        publishFromClassPath("org.ow2.asm:asm-commons:9.7", ClassRemapper.class);
        publishFromClassPath("me.lucko:jar-relocator:1.7", JarRelocator.class);

        repository.publish(LIBRARY, new PomBuilder().build(LIBRARY), FakeMavenRepository.jar(Map.of(
                "com/example/lib/Helper.class", helperClass(),
                "com/example/lib/Greeter.class", greeterClass())));

        libraryManager = LibraryManager.builder()
                .dataDirectory(temp.resolve("data"))
                .logAdapter(new NoopLogAdapter())
                .build();
        libraryManager.addRepository(repository.url());
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void relocatesPackagesAndReferences() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            libraryManager.loadDependencies(classLoader, List.of(LIBRARY), RELOCATIONS);

            Class<?> greeter = classLoader.loadClass("org.test.relocated.lib.Greeter");
            assertEquals("hello from quark", greeter.getMethod("greet").invoke(null));
            assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass("com.example.lib.Greeter"));
        }
    }

    @Test
    void cachesRelocatedJarsPerRelocationSet() throws Exception {
        List<Relocation> other = List.of(Relocation.of("com.example.lib", "org.other.lib"));

        try (IsolatedClassLoader first = new IsolatedClassLoader(); IsolatedClassLoader second = new IsolatedClassLoader()) {
            libraryManager.loadDependencies(first, List.of(LIBRARY), RELOCATIONS);
            // a second manager on the same directory, as two plugins sharing a cache would do
            LibraryManager otherManager = LibraryManager.builder()
                    .dataDirectory(temp.resolve("data"))
                    .logAdapter(new NoopLogAdapter())
                    .build();
            otherManager.addRepository(repository.url());
            otherManager.loadDependencies(second, List.of(LIBRARY), other);

            assertNotNull(second.loadClass("org.other.lib.Greeter"));
        }

        Path localRepository = libraryManager.getLocalRepository().getPath();
        Path firstJar = RelocationHandler.getRelocatedJarPath(libraryManager.getLocalRepository(), LIBRARY, RELOCATIONS);
        Path secondJar = RelocationHandler.getRelocatedJarPath(libraryManager.getLocalRepository(), LIBRARY, other);

        assertNotEquals(firstJar, secondJar);
        assertTrue(Files.isRegularFile(firstJar));
        assertTrue(Files.isRegularFile(secondJar));
        assertTrue(Files.isRegularFile(LIBRARY.getJarPath(localRepository)), "the downloaded original is shared");
        try (Stream<Path> files = Files.list(firstJar.getParent())) {
            assertTrue(files.noneMatch(file -> file.toString().endsWith(".tmp")), "no temporary files are left behind");
        }
    }

    @Test
    void reusesCachedRelocatedJar() throws Exception {
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            libraryManager.loadDependencies(classLoader, List.of(LIBRARY), RELOCATIONS);
        }
        Path relocated = RelocationHandler.getRelocatedJarPath(libraryManager.getLocalRepository(), LIBRARY, RELOCATIONS);
        FileTime marker = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(relocated, marker);

        LibraryManager restarted = LibraryManager.builder()
                .dataDirectory(temp.resolve("data"))
                .logAdapter(new NoopLogAdapter())
                .build();
        restarted.addRepository(repository.url());
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            restarted.loadDependencies(classLoader, List.of(LIBRARY), RELOCATIONS);
        }

        assertEquals(marker, Files.getLastModifiedTime(relocated));
    }

    @Test
    void relocationHashDependsOnRulesAndOrder() {
        List<Relocation> ab = List.of(Relocation.of("a", "x.a"), Relocation.of("b", "x.b"));
        List<Relocation> ba = List.of(Relocation.of("b", "x.b"), Relocation.of("a", "x.a"));

        assertEquals(RelocationHandler.relocationHash(ab), RelocationHandler.relocationHash(List.copyOf(ab)));
        assertNotEquals(RelocationHandler.relocationHash(ab), RelocationHandler.relocationHash(ba));
        assertNotEquals(RelocationHandler.relocationHash(ab), RelocationHandler.relocationHash(List.of(Relocation.of("a", "y.a"))));
        assertEquals(16, RelocationHandler.relocationHash(ab).length());
    }

    @Test
    void relocationSanitizesBracePlaceholders() {
        Relocation relocation = Relocation.of("com{}google{}gson", "my{}libs{}gson");

        assertEquals("com.google.gson", relocation.pattern());
        assertEquals("my.libs.gson", relocation.relocatedPattern());
    }

    private void publishFromClassPath(String coordinates, Class<?> type) throws Exception {
        Dependency dependency = Dependency.fromCoordinates(coordinates);
        Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        repository.publish(dependency, new PomBuilder().build(dependency), readAllBytes(jar));
    }

    private static byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code public class Helper { public static String name() { return "quark"; } }} */
    private static byte[] helperClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/lib/Helper", null, "java/lang/Object", null);
        MethodVisitor name = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "name", "()Ljava/lang/String;", null, null);
        name.visitCode();
        name.visitLdcInsn("quark");
        name.visitInsn(Opcodes.ARETURN);
        name.visitMaxs(0, 0);
        name.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** {@code public class Greeter { public static String greet() { return "hello from " + Helper.name(); } }} */
    private static byte[] greeterClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/lib/Greeter", null, "java/lang/Object", null);
        MethodVisitor greet = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "greet", "()Ljava/lang/String;", null, null);
        greet.visitCode();
        greet.visitLdcInsn("hello from ");
        greet.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/lib/Helper", "name", "()Ljava/lang/String;", false);
        greet.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
        greet.visitInsn(Opcodes.ARETURN);
        greet.visitMaxs(0, 0);
        greet.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
