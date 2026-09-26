package org.bxteam.quark.relocation.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Moves packages to new names: class references, descriptors, signatures, resource paths and string constants
 * that name a class or package under a relocated package (like the Shadow plugin does).
 *
 * <p>Runs inside the relocator class loader next to the downloaded ASM, so it may only use the JDK and ASM.</p>
 */
final class PackageRemapper extends Remapper {
    private final List<Rule> rules = new ArrayList<>();

    PackageRemapper(Map<String, String> relocations) {
        super(Opcodes.ASM9);
        relocations.forEach((from, to) -> rules.add(new Rule(from.replace('.', '/'), to.replace('.', '/'))));
        // the most specific package wins
        rules.sort(Comparator.comparingInt((Rule rule) -> rule.from.length()).reversed());
    }

    @Override
    public String map(String internalName) {
        return relocate(internalName, '/');
    }

    @Override
    public String mapPackageName(String name) {
        return relocate(name, '/');
    }

    @Override
    public Object mapValue(Object value) {
        if (value instanceof String string) {
            return mapString(string);
        }
        return super.mapValue(value);
    }

    /**
     * Relocates a string constant that is a class or package name, dotted ({@code org.foo.Bar}) or as a path
     * ({@code org/foo/bar.properties}, {@code /org/foo/bar.properties}).
     */
    String mapString(String string) {
        String dotted = relocate(string, '.');
        if (!dotted.equals(string)) {
            return dotted;
        }
        if (string.startsWith("/")) {
            return "/" + relocate(string.substring(1), '/');
        }
        return relocate(string, '/');
    }

    /**
     * @return the path of a resource or class file in the relocated JAR
     */
    String mapPath(String path) {
        return relocate(path, '/');
    }

    /**
     * @return a relocated dotted class name, as used in service files
     */
    String mapClassName(String className) {
        return relocate(className, '.');
    }

    private String relocate(String name, char separator) {
        for (Rule rule : rules) {
            String from = separator == '/' ? rule.from : rule.from.replace('/', '.');
            if (name.startsWith(from) && (name.length() == from.length() || name.charAt(from.length()) == separator)) {
                String to = separator == '/' ? rule.to : rule.to.replace('/', '.');
                return to + name.substring(from.length());
            }
        }
        return name;
    }

    private record Rule(String from, String to) {
    }
}
