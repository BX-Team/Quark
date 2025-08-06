package org.bxteam.quark.pom;

import org.bxteam.quark.dependency.Dependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Reads and parses Maven POM files to extract dependency information.
 *
 * @author BxTeam
 * @since 1.0.0
 */
public class PomReader {
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;

    public PomReader() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(false);
        this.documentBuilderFactory.setValidating(false);

        this.xPathFactory = XPathFactory.newInstance();
    }

    /**
     * Reads dependencies from a POM file.
     */
    @NotNull
    public PomInfo readPom(@NotNull Path pomFile) throws PomParsingException {
        requireNonNull(pomFile, "POM file cannot be null");

        if (!Files.exists(pomFile)) {
            throw new PomParsingException("POM file does not exist: " + pomFile);
        }

        try (InputStream inputStream = Files.newInputStream(pomFile)) {
            return readPom(inputStream, pomFile.toString());
        } catch (IOException e) {
            throw new PomParsingException("Failed to read POM file: " + pomFile, e);
        }
    }

    /**
     * Reads dependencies from a POM input stream.
     */
    @NotNull
    public PomInfo readPom(@NotNull InputStream inputStream, @NotNull String source) throws PomParsingException {
        requireNonNull(inputStream, "Input stream cannot be null");
        requireNonNull(source, "Source cannot be null");

        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(inputStream);

            return parsePomDocument(document, source);

        } catch (ParserConfigurationException e) {
            throw new PomParsingException("Failed to create XML parser", e);
        } catch (SAXException e) {
            throw new PomParsingException("Failed to parse POM XML from " + source, e);
        } catch (IOException e) {
            throw new PomParsingException("Failed to read POM from " + source, e);
        }
    }

    @NotNull
    private PomInfo parsePomDocument(@NotNull Document document, @NotNull String source) throws PomParsingException {
        XPath xpath = xPathFactory.newXPath();

        try {
            // Extract project information
            String groupId = getTextContent(xpath, document, "/project/groupId");
            String artifactId = getTextContent(xpath, document, "/project/artifactId");
            String version = getTextContent(xpath, document, "/project/version");

            // Extract parent information
            ParentInfo parentInfo = extractParentInfo(xpath, document);

            // Try parent if not found
            if (groupId == null && parentInfo != null) {
                groupId = parentInfo.groupId();
            }
            if (version == null && parentInfo != null) {
                version = parentInfo.version();
            }

            if (artifactId == null) {
                throw new PomParsingException("No artifactId found in POM: " + source);
            }

            // Extract properties for version resolution
            Map<String, String> properties = extractProperties(xpath, document);

            // Add built-in properties
            if (groupId != null) properties.put("project.groupId", groupId);
            if (artifactId != null) properties.put("project.artifactId", artifactId);
            if (version != null) properties.put("project.version", version);

            // Extract dependency management for version resolution
            Map<String, String> dependencyManagement = extractDependencyManagement(xpath, document, properties);

            // Extract dependencies
            List<Dependency> dependencies = extractDependencies(xpath, document, properties, dependencyManagement);

            return new PomInfo(
                    groupId,
                    artifactId,
                    version,
                    dependencies,
                    properties,
                    dependencyManagement,
                    parentInfo
            );

        } catch (XPathExpressionException e) {
            throw new PomParsingException("Failed to evaluate XPath expression", e);
        }
    }

    @Nullable
    private ParentInfo extractParentInfo(@NotNull XPath xpath, @NotNull Document document) throws XPathExpressionException {
        String parentGroupId = getTextContent(xpath, document, "/project/parent/groupId");
        String parentArtifactId = getTextContent(xpath, document, "/project/parent/artifactId");
        String parentVersion = getTextContent(xpath, document, "/project/parent/version");

        if (parentGroupId != null && parentArtifactId != null && parentVersion != null) {
            return new ParentInfo(parentGroupId, parentArtifactId, parentVersion);
        }

        return null;
    }

    @NotNull
    private Map<String, String> extractProperties(@NotNull XPath xpath, @NotNull Document document) throws XPathExpressionException {
        Map<String, String> properties = new HashMap<>();

        NodeList propertyNodes = (NodeList) xpath.evaluate("//properties/*", document, XPathConstants.NODESET);

        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Node propertyNode = propertyNodes.item(i);
            String name = propertyNode.getNodeName();
            String value = propertyNode.getTextContent();

            if (name != null && value != null) {
                properties.put(name.trim(), value.trim());
            }
        }

        return properties;
    }

    @NotNull
    private Map<String, String> extractDependencyManagement(@NotNull XPath xpath, @NotNull Document document,
                                                            @NotNull Map<String, String> properties) throws XPathExpressionException {
        Map<String, String> managedVersions = new HashMap<>();

        NodeList dependencyNodes = (NodeList) xpath.evaluate("//dependencyManagement/dependencies/dependency", document, XPathConstants.NODESET);

        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node dependencyNode = dependencyNodes.item(i);

            String groupId = getChildNodeText(dependencyNode, "groupId");
            String artifactId = getChildNodeText(dependencyNode, "artifactId");
            String version = getChildNodeText(dependencyNode, "version");

            if (groupId != null && artifactId != null && version != null) {
                groupId = resolveProperties(groupId, properties);
                artifactId = resolveProperties(artifactId, properties);
                version = resolveProperties(version, properties);

                managedVersions.put(groupId + ":" + artifactId, version);
            }
        }

        return managedVersions;
    }

    @NotNull
    private List<Dependency> extractDependencies(@NotNull XPath xpath, @NotNull Document document,
                                                 @NotNull Map<String, String> properties,
                                                 @NotNull Map<String, String> dependencyManagement) throws XPathExpressionException {
        List<Dependency> dependencies = new ArrayList<>();

        // Try multiple XPath expressions to find dependencies
        String[] xpathExpressions = {
                "//dependencies/dependency",
                "//project/dependencies/dependency",
                "/project/dependencies/dependency"
        };

        NodeList dependencyNodes = null;
        for (String expression : xpathExpressions) {
            dependencyNodes = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
            if (dependencyNodes.getLength() > 0) {
                break;
            }
        }

        if (dependencyNodes == null || dependencyNodes.getLength() == 0) {
            return dependencies; // No dependencies found
        }

        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node dependencyNode = dependencyNodes.item(i);

            try {
                Dependency dependency = parseDependencyNode(dependencyNode, properties, dependencyManagement);
                if (dependency != null && shouldIncludeDependency(dependencyNode)) {
                    dependencies.add(dependency);
                }
            } catch (Exception e) {
                // Log and skip invalid dependency entries
                System.err.println("Skipping invalid dependency entry: " + e.getMessage());
            }
        }

        return dependencies;
    }

    @Nullable
    private Dependency parseDependencyNode(@NotNull Node dependencyNode,
                                           @NotNull Map<String, String> properties,
                                           @NotNull Map<String, String> dependencyManagement) {
        String groupId = getChildNodeText(dependencyNode, "groupId");
        String artifactId = getChildNodeText(dependencyNode, "artifactId");
        String version = getChildNodeText(dependencyNode, "version");
        String classifier = getChildNodeText(dependencyNode, "classifier");

        if (groupId == null || artifactId == null) {
            return null; // Required fields missing
        }

        // Resolve properties
        groupId = resolveProperties(groupId, properties);
        artifactId = resolveProperties(artifactId, properties);

        // Resolve version from dependency management if not specified
        if (version == null || version.trim().isEmpty()) {
            version = dependencyManagement.get(groupId + ":" + artifactId);
        }

        if (version != null) {
            version = resolveProperties(version, properties);
        }

        if (version == null || version.trim().isEmpty()) {
            return null; // No version available - will need to resolve from metadata
        }

        // Handle classifier
        if (classifier != null) {
            classifier = resolveProperties(classifier, properties);
        }

        return Dependency.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .classifier(classifier)
                .build();
    }

    private boolean shouldIncludeDependency(@NotNull Node dependencyNode) {
        String scope = getChildNodeText(dependencyNode, "scope");
        String optional = getChildNodeText(dependencyNode, "optional");

        // Default scope
        if (scope == null || scope.trim().isEmpty()) {
            scope = "compile";
        }

        // Include compile and runtime scope dependencies
        if (!"compile".equals(scope) && !"runtime".equals(scope)) {
            return false;
        }

        // Exclude optional dependencies
        return !"true".equals(optional);
    }

    @NotNull
    private String resolveProperties(@NotNull String value, @NotNull Map<String, String> properties) {
        String resolved = value;

        // Simple property resolution - handle ${property.name} format
        int maxIterations = 10; // Prevent infinite loops
        int iteration = 0;

        while (resolved.contains("${") && iteration < maxIterations) {
            int start = resolved.indexOf("${");
            int end = resolved.indexOf("}", start);

            if (end == -1) break; // Malformed property reference

            String propertyName = resolved.substring(start + 2, end);
            String propertyValue = properties.get(propertyName);

            if (propertyValue != null) {
                resolved = resolved.substring(0, start) + propertyValue + resolved.substring(end + 1);
            } else {
                // Property not found - leave as is
                break;
            }

            iteration++;
        }

        return resolved;
    }

    @Nullable
    private String getTextContent(@NotNull XPath xpath, @NotNull Document document, @NotNull String expression) throws XPathExpressionException {
        Node node = (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
        return node != null ? node.getTextContent().trim() : null;
    }

    @Nullable
    private String getChildNodeText(@NotNull Node parentNode, @NotNull String childName) {
        NodeList childNodes = parentNode.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE &&
                    childName.equals(childNode.getNodeName())) {
                String text = childNode.getTextContent();
                return text != null ? text.trim() : null;
            }
        }
        return null;
    }

    /**
     * Parent POM information.
     */
    public record ParentInfo(
            @NotNull String groupId,
            @NotNull String artifactId,
            @NotNull String version
    ) {
        public ParentInfo {
            requireNonNull(groupId, "Group ID cannot be null");
            requireNonNull(artifactId, "Artifact ID cannot be null");
            requireNonNull(version, "Version cannot be null");
        }

        /**
         * Creates a Dependency for this parent.
         */
        @NotNull
        public Dependency toDependency() {
            return Dependency.of(groupId, artifactId, version);
        }
    }

    /**
     * Information extracted from a POM file.
     */
    public record PomInfo(String groupId, String artifactId, String version, List<Dependency> dependencies,
                          Map<String, String> properties, Map<String, String> dependencyManagement,
                          ParentInfo parentInfo) {
        public PomInfo(@Nullable String groupId,
                       @NotNull String artifactId,
                       @Nullable String version,
                       @NotNull List<Dependency> dependencies,
                       @NotNull Map<String, String> properties,
                       @NotNull Map<String, String> dependencyManagement,
                       @Nullable ParentInfo parentInfo) {
            this.groupId = groupId;
            this.artifactId = requireNonNull(artifactId, "Artifact ID cannot be null");
            this.version = version;
            this.dependencies = List.copyOf(dependencies);
            this.properties = Map.copyOf(properties);
            this.dependencyManagement = Map.copyOf(dependencyManagement);
            this.parentInfo = parentInfo;
        }

        @Override
        @Nullable
        public String groupId() {
            return groupId;
        }

        @Override
        @NotNull
        public String artifactId() {
            return artifactId;
        }

        @Override
        @Nullable
        public String version() {
            return version;
        }

        @Override
        @NotNull
        public List<Dependency> dependencies() {
            return dependencies;
        }

        /**
         * Gets runtime dependencies (filtering out test and provided scope).
         */
        @NotNull
        public List<Dependency> getRuntimeDependencies() {
            return dependencies; // Already filtered during parsing
        }

        @Override
        @NotNull
        public Map<String, String> properties() {
            return properties;
        }

        @Override
        @NotNull
        public Map<String, String> dependencyManagement() {
            return dependencyManagement;
        }

        @Override
        @Nullable
        public ParentInfo parentInfo() {
            return parentInfo;
        }

        /**
         * Checks if this POM has a parent.
         */
        public boolean hasParent() {
            return parentInfo != null;
        }

        /**
         * Gets the project dependency if groupId and version are available.
         */
        @Nullable
        public Dependency getProjectDependency() {
            if (groupId != null && version != null) {
                return Dependency.of(groupId, artifactId, version);
            }
            return null;
        }
    }

    /**
     * Exception thrown when POM parsing fails.
     */
    public static class PomParsingException extends Exception {
        public PomParsingException(String message) {
            super(message);
        }

        public PomParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
