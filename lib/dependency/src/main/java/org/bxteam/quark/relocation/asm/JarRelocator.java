package org.bxteam.quark.relocation.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Copies a JAR with packages moved to new names. Replaces {@code me.lucko:jar-relocator}, so the only runtime
 * dependency of relocation is ASM.
 *
 * <ul>
 *     <li>classes are rewritten with {@link ClassRemapper}, string constants naming relocated classes included;</li>
 *     <li>multi-release classes under {@code META-INF/versions/<n>/} are relocated too;</li>
 *     <li>{@code META-INF/services} files are renamed and their entries relocated;</li>
 *     <li>other resources under relocated packages are moved with them;</li>
 *     <li>signature files are dropped, the relocated classes would no longer match them.</li>
 * </ul>
 *
 * <p>Loaded by the relocator class loader next to the downloaded ASM and called through reflection, so it may only
 * use the JDK and ASM.</p>
 */
public final class JarRelocator {
    private static final String VERSIONS = "META-INF/versions/";
    private static final String SERVICES = "META-INF/services/";

    private final File input;
    private final File output;
    private final PackageRemapper remapper;

    /**
     * @param input the JAR to read
     * @param output the relocated JAR to write
     * @param relocations dotted package names to their new names
     */
    public JarRelocator(File input, File output, Map<String, String> relocations) {
        this.input = input;
        this.output = output;
        this.remapper = new PackageRemapper(relocations);
    }

    /**
     * Writes the relocated JAR.
     *
     * @throws IOException if the input cannot be read or the output cannot be written
     */
    public void run() throws IOException {
        Set<String> written = new HashSet<>();
        try (JarFile jar = new JarFile(input, false);
             OutputStream file = Files.newOutputStream(output.toPath());
             JarOutputStream out = new JarOutputStream(new BufferedOutputStream(file))) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || isSignature(name)) {
                    continue;
                }

                byte[] bytes;
                try (InputStream in = jar.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }

                String newName;
                byte[] newBytes;
                if (name.endsWith(".class") && !name.endsWith("module-info.class")) {
                    ClassReader reader = new ClassReader(bytes);
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassRemapper(writer, remapper), 0);
                    newName = versionPrefix(name) + remapper.map(reader.getClassName()) + ".class";
                    newBytes = writer.toByteArray();
                } else if (name.startsWith(SERVICES) && name.indexOf('/', SERVICES.length()) < 0) {
                    newName = SERVICES + remapper.mapClassName(name.substring(SERVICES.length()));
                    newBytes = relocateServiceFile(bytes);
                } else {
                    String prefix = versionPrefix(name);
                    newName = prefix + remapper.mapPath(name.substring(prefix.length()));
                    newBytes = bytes;
                }

                if (!written.add(newName)) {
                    continue;
                }
                JarEntry relocated = new JarEntry(newName);
                relocated.setTime(entry.getTime());
                out.putNextEntry(relocated);
                out.write(newBytes);
                out.closeEntry();
            }
        }
    }

    private byte[] relocateServiceFile(byte[] bytes) {
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\R", -1);
        ByteArrayOutputStream result = new ByteArrayOutputStream(bytes.length + 64);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int comment = line.indexOf('#');
            String className = (comment >= 0 ? line.substring(0, comment) : line).trim();
            String relocated = className.isEmpty() ? line : line.replace(className, remapper.mapClassName(className));
            result.writeBytes(relocated.getBytes(StandardCharsets.UTF_8));
            if (i < lines.length - 1) {
                result.write('\n');
            }
        }
        return result.toByteArray();
    }

    private static String versionPrefix(String name) {
        if (!name.startsWith(VERSIONS)) {
            return "";
        }
        int end = name.indexOf('/', VERSIONS.length());
        return end < 0 ? "" : name.substring(0, end + 1);
    }

    private static boolean isSignature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/") && upper.indexOf('/', "META-INF/".length()) < 0
                && (upper.endsWith(".SF") || upper.endsWith(".DSA") || upper.endsWith(".RSA") || upper.endsWith(".EC"));
    }
}
