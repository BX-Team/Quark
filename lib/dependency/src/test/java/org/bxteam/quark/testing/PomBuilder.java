package org.bxteam.quark.testing;

import org.bxteam.quark.dependency.Dependency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds minimal POM files for {@link FakeMavenRepository}.
 */
public final class PomBuilder {
    private final List<String> dependencies = new ArrayList<>();
    private final List<String> managed = new ArrayList<>();
    private final Map<String, String> properties = new LinkedHashMap<>();
    private String parent;

    public PomBuilder dependency(String coordinates) {
        return dependency(coordinates, null, false);
    }

    /** Adds a dependency; {@code coordinates} may omit the version ({@code group:artifact}). */
    public PomBuilder dependency(String coordinates, String scope, boolean optional) {
        String[] parts = coordinates.split(":");
        StringBuilder xml = new StringBuilder("<dependency>")
                .append("<groupId>").append(parts[0]).append("</groupId>")
                .append("<artifactId>").append(parts[1]).append("</artifactId>");
        if (parts.length > 2) xml.append("<version>").append(parts[2]).append("</version>");
        if (scope != null) xml.append("<scope>").append(scope).append("</scope>");
        if (optional) xml.append("<optional>true</optional>");
        dependencies.add(xml.append("</dependency>").toString());
        return this;
    }

    public PomBuilder managed(String coordinates) {
        String[] parts = coordinates.split(":");
        managed.add("<dependency><groupId>" + parts[0] + "</groupId><artifactId>" + parts[1]
                + "</artifactId><version>" + parts[2] + "</version></dependency>");
        return this;
    }

    public PomBuilder property(String name, String value) {
        properties.put(name, value);
        return this;
    }

    public PomBuilder parent(String coordinates) {
        String[] parts = coordinates.split(":");
        parent = "<parent><groupId>" + parts[0] + "</groupId><artifactId>" + parts[1]
                + "</artifactId><version>" + parts[2] + "</version></parent>";
        return this;
    }

    public String build(Dependency dependency) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project>")
                .append("<modelVersion>4.0.0</modelVersion>");
        if (parent != null) xml.append(parent);
        xml.append("<groupId>").append(dependency.getGroupId()).append("</groupId>")
                .append("<artifactId>").append(dependency.getArtifactId()).append("</artifactId>")
                .append("<version>").append(dependency.getVersion()).append("</version>");
        if (!properties.isEmpty()) {
            xml.append("<properties>");
            properties.forEach((name, value) -> xml.append('<').append(name).append('>').append(value).append("</").append(name).append('>'));
            xml.append("</properties>");
        }
        if (!managed.isEmpty()) {
            xml.append("<dependencyManagement><dependencies>");
            managed.forEach(xml::append);
            xml.append("</dependencies></dependencyManagement>");
        }
        if (!dependencies.isEmpty()) {
            xml.append("<dependencies>");
            dependencies.forEach(xml::append);
            xml.append("</dependencies>");
        }
        return xml.append("</project>").toString();
    }
}
