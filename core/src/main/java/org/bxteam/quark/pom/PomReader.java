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
 * <p>This class provides functionality to read and parse Maven Project Object Model (POM)
 * files, extracting essential information such as artifact coordinates, dependencies,
 * properties, and dependency management.</p>
 */
public class PomReader {
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;

    /**
     * Creates a new PomReader with default XML parser configuration.
     */
    public PomReader() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(false);
        this.documentBuilderFactory.setValidating(false);

        this.xPathFactory = XPathFactory.newInstance();
    }

    /**
     * Reads dependencies from a POM file.
     *
     * @param pomFile the path to the POM file
     * @return the parsed POM information
     * @throws PomParsingException if parsing fails
     * @throws NullPointerException if pomFile is null
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
     *
     * @param inputStream the input stream containing the POM content
     * @param source the source description for error reporting
     * @return the parsed POM information
     * @throws PomParsingException if parsing fails
     * @throws NullPointerException if any parameter is null
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

    /**
     * Parses a POM document into structured information.
     *
     * @param document the XML document representing the POM
     * @param source the source description for error reporting
     * @return the parsed POM information
     * @throws PomParsingException if parsing fails
     */
    @NotNull
    private PomInfo parsePomDocument(@NotNull Document document, @NotNull String source) throws PomParsingException {
        XPath xpath = xPathFactory.newXPath();

        try {
            String groupId = getTextContent(xpath, document, "/project/groupId");
            String artifactId = getTextContent(xpath, document, "/project/artifactId");
            String version = getTextContent(xpath, document, "/project/version");

            ParentInfo parentInfo = extractParentInfo(xpath, document);

            if (groupId == null && parentInfo != null) {
                groupId = parentInfo.groupId();
            }
            if (version == null && parentInfo != null) {
                version = parentInfo.version();
            }

            if (artifactId == null) {
                throw new PomParsingException("No artifactId found in POM: " + source);
            }

            Map<String, String> properties = extractProperties(xpath, document);

            if (groupId != null) properties.put("project.groupId", groupId);
            if (artifactId != null) properties.put("project.artifactId", artifactId);
            if (version != null) properties.put("project.version", version);

            Map<String, String> dependencyManagement = extractDependencyManagement(xpath, document, properties);

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

    /**
     * Extracts parent POM information if present.
     *
     * @param xpath the XPath evaluator
     * @param document the XML document
     * @return the parent POM information, or null if not present
     * @throws XPathExpressionException if XPath evaluation fails
     */
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

    /**
     * Extracts properties from the POM.
     *
     * @param xpath the XPath evaluator
     * @param document the XML document
     * @return a map of property names to values
     * @throws XPathExpressionException if XPath evaluation fails
     */
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

    /**
     * Extracts dependency management information.
     *
     * @param xpath the XPath evaluator
     * @param document the XML document
     * @param properties the properties for resolving property references
     * @return a map of dependency coordinates to versions
     * @throws XPathExpressionException if XPath evaluation fails
     */
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

    /**
     * Extracts dependencies from the POM.
     *
     * @param xpath the XPath evaluator
     * @param document the XML document
     * @param properties the properties for resolving property references
     * @param dependencyManagement the dependency management information
     * @return a list of dependencies
     * @throws XPathExpressionException if XPath evaluation fails
     */
    @NotNull
    private List<Dependency> extractDependencies(@NotNull XPath xpath, @NotNull Document document,
                                                 @NotNull Map<String, String> properties,
                                                 @NotNull Map<String, String> dependencyManagement) throws XPathExpressionException {
        List<Dependency> dependencies = new ArrayList<>();

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
            return dependencies;
        }

        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node dependencyNode = dependencyNodes.item(i);

            try {
                Dependency dependency = parseDependencyNode(dependencyNode, properties, dependencyManagement);
                if (dependency != null && shouldIncludeDependency(dependencyNode)) {
                    dependencies.add(dependency);
                }
            } catch (Exception e) {
                System.err.println("Skipping invalid dependency entry: " + e.getMessage());
            }
        }

        return dependencies;
    }

    /**
     * Parses a dependency node into a Dependency object.
     *
     * @param dependencyNode the XML node for the dependency
     * @param properties the properties for resolving property references
     * @param dependencyManagement the dependency management information
     * @return the parsed Dependency, or null if invalid
     */
    @Nullable
    private Dependency parseDependencyNode(@NotNull Node dependencyNode,
                                           @NotNull Map<String, String> properties,
                                           @NotNull Map<String, String> dependencyManagement) {
        String groupId = getChildNodeText(dependencyNode, "groupId");
        String artifactId = getChildNodeText(dependencyNode, "artifactId");
        String version = getChildNodeText(dependencyNode, "version");
        String classifier = getChildNodeText(dependencyNode, "classifier");

        if (groupId == null || artifactId == null) {
            return null;
        }

        groupId = resolveProperties(groupId, properties);
        artifactId = resolveProperties(artifactId, properties);

        if (version == null || version.trim().isEmpty()) {
            version = dependencyManagement.get(groupId + ":" + artifactId);
        }

        if (version != null) {
            version = resolveProperties(version, properties);
        }

        if (version == null || version.trim().isEmpty()) {
            return null;
        }

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

    /**
     * Determines if a dependency should be included in the result.
     *
     * @param dependencyNode the XML node for the dependency
     * @return true if the dependency should be included, false otherwise
     */
    private boolean shouldIncludeDependency(@NotNull Node dependencyNode) {
        String scope = getChildNodeText(dependencyNode, "scope");
        String optional = getChildNodeText(dependencyNode, "optional");

        if (scope == null || scope.trim().isEmpty()) {
            scope = "compile";
        }

        if (!"compile".equals(scope) && !"runtime".equals(scope)) {
            return false;
        }

        return !"true".equals(optional);
    }

    /**
     * Resolves property references in a string.
     *
     * @param value the string potentially containing property references
     * @param properties the properties to use for resolution
     * @return the string with property references resolved
     */
    @NotNull
    private String resolveProperties(@NotNull String value, @NotNull Map<String, String> properties) {
        String resolved = value;

        int maxIterations = 10;
        int iteration = 0;

        while (resolved.contains("${") && iteration < maxIterations) {
            int start = resolved.indexOf("${");
            int end = resolved.indexOf("}", start);

            if (end == -1) break;

            String propertyName = resolved.substring(start + 2, end);
            String propertyValue = properties.get(propertyName);

            if (propertyValue != null) {
                resolved = resolved.substring(0, start) + propertyValue + resolved.substring(end + 1);
            } else {
                break;
            }

            iteration++;
        }

        return resolved;
    }

    /**
     * Gets text content from an XML node selected by XPath.
     *
     * @param xpath the XPath evaluator
     * @param document the XML document
     * @param expression the XPath expression
     * @return the text content, or null if not found
     * @throws XPathExpressionException if XPath evaluation fails
     */
    @Nullable
    private String getTextContent(@NotNull XPath xpath, @NotNull Document document, @NotNull String expression) throws XPathExpressionException {
        Node node = (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
        return node != null ? node.getTextContent().trim() : null;
    }

    /**
     * Gets text content from a child node.
     *
     * @param parentNode the parent XML node
     * @param childName the name of the child node
     * @return the text content, or null if not found
     */
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
     *
     * @param groupId the parent group ID
     * @param artifactId the parent artifact ID
     * @param version the parent version
     */
    public record ParentInfo(
            @NotNull String groupId,
            @NotNull String artifactId,
            @NotNull String version
    ) {
        /**
         * Creates a new ParentInfo.
         *
         * @param groupId the parent group ID
         * @param artifactId the parent artifact ID
         * @param version the parent version
         * @throws NullPointerException if any parameter is null
         */
        public ParentInfo {
            requireNonNull(groupId, "Group ID cannot be null");
            requireNonNull(artifactId, "Artifact ID cannot be null");
            requireNonNull(version, "Version cannot be null");
        }

        /**
         * Creates a Dependency for this parent.
         *
         * @return a Dependency object representing this parent
         */
        @NotNull
        public Dependency toDependency() {
            return Dependency.of(groupId, artifactId, version);
        }
    }

    /**
     * Information extracted from a POM file.
     *
     * @param groupId the group ID, or null if not specified
     * @param artifactId the artifact ID
     * @param version the version, or null if not specified
     * @param dependencies the list of dependencies
     * @param properties the map of properties
     * @param dependencyManagement the map of dependency management entries
     * @param parentInfo the parent POM information, or null if not present
     */
    public record PomInfo(String groupId, String artifactId, String version, List<Dependency> dependencies,
                          Map<String, String> properties, Map<String, String> dependencyManagement,
                          ParentInfo parentInfo) {
        /**
         * Creates a new PomInfo.
         *
         * @param groupId the group ID, or null if not specified
         * @param artifactId the artifact ID
         * @param version the version, or null if not specified
         * @param dependencies the list of dependencies
         * @param properties the map of properties
         * @param dependencyManagement the map of dependency management entries
         * @param parentInfo the parent POM information, or null if not present
         * @throws NullPointerException if artifactId is null
         */
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

        /**
         * Gets runtime dependencies (filtering out test and provided scope).
         *
         * @return the list of runtime dependencies
         */
        @NotNull
        public List<Dependency> getRuntimeDependencies() {
            return dependencies;
        }

        /**
         * Checks if this POM has a parent.
         *
         * @return true if this POM has a parent, false otherwise
         */
        public boolean hasParent() {
            return parentInfo != null;
        }

        /**
         * Gets the project dependency if groupId and version are available.
         *
         * @return the project dependency, or null if insufficient information
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
        /**
         * Creates a new PomParsingException with the specified message.
         *
         * @param message the detail message
         */
        public PomParsingException(String message) {
            super(message);
        }

        /**
         * Creates a new PomParsingException with the specified message and cause.
         *
         * @param message the detail message
         * @param cause the cause of this exception
         */
        public PomParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
