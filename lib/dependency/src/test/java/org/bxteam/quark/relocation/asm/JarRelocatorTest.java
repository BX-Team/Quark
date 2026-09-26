package org.bxteam.quark.relocation.asm;

import org.bxteam.quark.testing.FakeMavenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarRelocatorTest {
    @TempDir
    Path temp;

    private Map<String, byte[]> relocate(Map<String, byte[]> entries, Map<String, String> relocations) throws IOException {
        Path input = temp.resolve("in.jar");
        Path output = temp.resolve("out.jar");
        Files.write(input, FakeMavenRepository.jar(entries));
        new JarRelocator(input.toFile(), output.toFile(), relocations).run();

        Map<String, byte[]> result = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(output.toFile())) {
            for (var entry : java.util.Collections.list(jar.entries())) {
                result.put(entry.getName(), jar.getInputStream(entry).readAllBytes());
            }
        }
        return result;
    }

    @Test
    void relocatesClassesStringsServicesAndResources() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("com/example/lib/Api.class", classWithStrings("com/example/lib/Api",
                "com.example.lib.impl.Provider", "com/example/lib/data.properties", "/com/example/lib/data.properties",
                "com.example.library", "unrelated text"));
        entries.put("META-INF/versions/17/com/example/lib/Api.class", classWithStrings("com/example/lib/Api"));
        entries.put("META-INF/services/com.example.lib.Api", "# providers\ncom.example.lib.impl.Provider # default\n".getBytes(StandardCharsets.UTF_8));
        entries.put("com/example/lib/data.properties", "key=value".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/LIB.SF", new byte[]{1});
        entries.put("META-INF/LIB.RSA", new byte[]{1});
        entries.put("other/readme.txt", new byte[]{2});

        Map<String, byte[]> relocated = relocate(entries, Map.of("com.example.lib", "my.plugin.libs.lib"));

        assertTrue(relocated.containsKey("my/plugin/libs/lib/Api.class"), relocated.keySet().toString());
        assertTrue(relocated.containsKey("META-INF/versions/17/my/plugin/libs/lib/Api.class"), relocated.keySet().toString());
        assertTrue(relocated.containsKey("my/plugin/libs/lib/data.properties"));
        assertTrue(relocated.containsKey("other/readme.txt"));
        assertTrue(relocated.keySet().stream().noneMatch(name -> name.endsWith(".SF") || name.endsWith(".RSA")), "signatures are dropped");
        assertEquals("# providers\nmy.plugin.libs.lib.impl.Provider # default\n",
                new String(relocated.get("META-INF/services/my.plugin.libs.lib.Api"), StandardCharsets.UTF_8));

        assertEquals(List.of("my.plugin.libs.lib.impl.Provider", "my/plugin/libs/lib/data.properties", "/my/plugin/libs/lib/data.properties",
                "com.example.library", "unrelated text"), strings(relocated.get("my/plugin/libs/lib/Api.class")));
    }

    @Test
    void theMostSpecificRelocationWins() {
        Map<String, String> relocations = new LinkedHashMap<>();
        relocations.put("com.example", "a");
        relocations.put("com.example.lib", "b");
        PackageRemapper remapper = new PackageRemapper(relocations);

        assertEquals("b/Api", remapper.map("com/example/lib/Api"));
        assertEquals("a/other/Api", remapper.map("com/example/other/Api"));
        assertEquals("com/examples/Api", remapper.map("com/examples/Api"));
    }

    /** A class whose static method {@code strings()} pushes the given string constants. */
    private static byte[] classWithStrings(String name, String... strings) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "strings", "()V", null, null);
        method.visitCode();
        for (String string : strings) {
            method.visitLdcInsn(string);
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static List<String> strings(byte[] classBytes) {
        List<String> strings = new ArrayList<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String string) {
                            strings.add(string);
                        }
                    }
                };
            }
        }, 0);
        return strings;
    }
}
