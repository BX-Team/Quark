package org.bxteam.quark.pom;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.DependencyCollector;
import org.bxteam.quark.dependency.DependencyException;
import org.bxteam.quark.repository.LocalRepository;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static java.util.Objects.requireNonNull;

/**
 * Scans POM XML files to find transitive dependencies.
 */
public class PomXmlScanner implements DependencyScanner {
    private final List<Repository> repositories;
    private final LocalRepository localRepository;
    private final Set<String> processedDependencies = new HashSet<>();
    private final Set<String> currentlyProcessing = new HashSet<>();

    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;

    public PomXmlScanner(@NotNull List<Repository> repositories, @NotNull LocalRepository localRepository) {
        this.repositories = new ArrayList<>(requireNonNull(repositories, "Repositories cannot be null"));
        this.localRepository = requireNonNull(localRepository, "Local repository cannot be null");

        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(false);
        this.documentBuilderFactory.setValidating(false);

        this.xPathFactory = XPathFactory.newInstance();
    }

    @Override
    public void findAllChildren(@NotNull DependencyCollector collector, @NotNull Dependency dependency) {
        requireNonNull(collector, "Collector cannot be null");
        requireNonNull(dependency, "Dependency cannot be null");

        String dependencyKey = dependency.getGroupArtifactId() + ":" + dependency.getVersion();

        if (processedDependencies.contains(dependencyKey)) {
            return;
        }

        if (currentlyProcessing.contains(dependencyKey)) {
            return;
        }

        currentlyProcessing.add(dependencyKey);

        try {
            if (!collector.hasScannedDependency(dependency)) {
                collector.addScannedDependency(dependency);
            }

            Path pomFile = findPomFile(dependency);
            if (pomFile == null) {
                return;
            }

            List<Dependency> transitiveDependencies = parsePomFile(pomFile, dependency);

            for (Dependency transitiveDep : transitiveDependencies) {
                if (!collector.hasScannedDependency(transitiveDep)) {
                    collector.addScannedDependency(transitiveDep);
                }
            }

        } catch (Exception e) {
        } finally {
            processedDependencies.add(dependencyKey);
            currentlyProcessing.remove(dependencyKey);
        }
    }

    @Override
    public boolean canHandle(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");

        Path pomFile = findPomFile(dependency);
        return pomFile != null;
    }

    @Override
    public String getDescription() {
        return "POM XML Scanner (supports Maven POM files with transitive dependencies)";
    }

    /**
     * Finds the POM file for a dependency.
     */
    @Nullable
    private Path findPomFile(@NotNull Dependency dependency) {
        Path localPomPath = dependency.toPomXml(localRepository).toPath();
        if (Files.exists(localPomPath) && isValidPomFile(localPomPath)) {
            return localPomPath;
        }

        return null;
    }

    /**
     * Validates that a file is a valid POM file.
     */
    private boolean isValidPomFile(@NotNull Path pomFile) {
        try {
            if (!Files.exists(pomFile) || !Files.isRegularFile(pomFile) || Files.size(pomFile) == 0) {
                return false;
            }

            String content = Files.readString(pomFile).trim();

            if (content.isEmpty()) {
                return false;
            }

            boolean hasValidXmlStructure = content.contains("<") && content.contains(">");
            boolean hasProjectTag = content.contains("<project") || content.contains("<project>");
            boolean isNotHtmlError = !content.toLowerCase().contains("<html") &&
                    !content.toLowerCase().contains("<!doctype html");

            return hasValidXmlStructure && hasProjectTag && isNotHtmlError;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parses a POM file to extract dependencies.
     */
    @NotNull
    private List<Dependency> parsePomFile(@NotNull Path pomFile, @NotNull Dependency parentDependency) throws Exception {
        List<Dependency> dependencies = new ArrayList<>();

        DocumentBuilder builder;
        try {
            builder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new DependencyException("Failed to create XML parser", e);
        }

        Document document;
        try (InputStream inputStream = Files.newInputStream(pomFile)) {
            document = builder.parse(inputStream);
        } catch (SAXException e) {
            throw new DependencyException("Failed to parse POM XML for " + parentDependency, e);
        }

        XPath xpath = xPathFactory.newXPath();
        NodeList dependencyNodes;

        try {
            String[] xpathExpressions = {
                    "//dependencies/dependency",
                    "//project/dependencies/dependency",
                    "/project/dependencies/dependency"
            };

            dependencyNodes = null;
            for (String expression : xpathExpressions) {
                dependencyNodes = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
                if (dependencyNodes.getLength() > 0) {
                    break;
                }
            }

            if (dependencyNodes == null || dependencyNodes.getLength() == 0) {
                return dependencies;
            }

        } catch (XPathExpressionException e) {
            throw new DependencyException("Failed to evaluate XPath expression", e);
        }

        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node dependencyNode = dependencyNodes.item(i);

            try {
                Dependency dep = parseDependencyNode(dependencyNode, parentDependency);
                if (dep != null && shouldIncludeDependency(dependencyNode)) {
                    dependencies.add(dep);
                }
            } catch (Exception e) { }
        }

        return dependencies;
    }

    /**
     * Parses a single dependency node.
     */
    @Nullable
    private Dependency parseDependencyNode(@NotNull Node dependencyNode, @NotNull Dependency parentDependency) {
        String groupId = getChildNodeText(dependencyNode, "groupId");
        String artifactId = getChildNodeText(dependencyNode, "artifactId");
        String version = getChildNodeText(dependencyNode, "version");

        if (groupId == null || artifactId == null) {
            return null;
        }

        if (version == null || version.isEmpty()) {
            return null;
        }

        if (version.startsWith("${") && version.endsWith("}")) {
            return null;
        }

        return Dependency.of(groupId, artifactId, version);
    }

    /**
     * Checks if a dependency should be included based on scope and optional flag.
     */
    private boolean shouldIncludeDependency(@NotNull Node dependencyNode) {
        String scope = getChildNodeText(dependencyNode, "scope");
        String optional = getChildNodeText(dependencyNode, "optional");

        if (scope == null || scope.isEmpty()) {
            scope = "compile";
        }

        if (!"compile".equals(scope) && !"runtime".equals(scope)) {
            return false;
        }

        return !"true".equals(optional);
    }

    /**
     * Gets text content of a child node.
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
     * Clears the processed dependencies cache.
     * This should be called when starting a new dependency resolution process.
     */
    public void clearCache() {
        processedDependencies.clear();
        currentlyProcessing.clear();
    }

    /**
     * Gets the number of processed dependencies.
     *
     * @return the number of dependencies that have been processed
     */
    public int getProcessedCount() {
        return processedDependencies.size();
    }

    @Override
    public String toString() {
        return "PomXmlScanner{" +
                "repositories=" + repositories.size() +
                ", localRepository=" + localRepository +
                ", processed=" + processedDependencies.size() +
                '}';
    }
}
