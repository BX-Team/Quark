package org.bxteam.quark.pom;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.DependencyCollector;
import org.bxteam.quark.repository.LocalRepository;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
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

import static java.util.Objects.requireNonNull;

/**
 * Scans POM XML files to find transitive dependencies.
 *
 * <p>This implementation reads POM files from the local repository
 * (downloaded by DependencyDownloader) and parses them to discover
 * transitive dependencies.</p>
 */
public class PomXmlScanner implements DependencyScanner {
    private final List<Repository> repositories;
    private final LocalRepository localRepository;
    private final Set<String> processedDependencies = new HashSet<>();

    public PomXmlScanner(@NotNull List<Repository> repositories, @NotNull LocalRepository localRepository) {
        this.repositories = new ArrayList<>(requireNonNull(repositories, "Repositories cannot be null"));
        this.localRepository = requireNonNull(localRepository, "Local repository cannot be null");
    }

    @Override
    public void findAllChildren(@NotNull DependencyCollector collector, @NotNull Dependency dependency) {
        requireNonNull(collector, "Collector cannot be null");
        requireNonNull(dependency, "Dependency cannot be null");

        String dependencyKey = dependency.getGroupArtifactId() + ":" + dependency.getVersion();

        if (processedDependencies.contains(dependencyKey)) {
            return;
        }

        if (collector.hasScannedDependency(dependency)) {
            return;
        }

        processedDependencies.add(dependencyKey);

        collector.addScannedDependency(dependency);

        Path pomFile = findPomFile(dependency);
        if (pomFile == null) {
            return;
        }

        try {
            List<Dependency> transitiveDependencies = parsePomFile(pomFile);

            for (Dependency transitiveDep : transitiveDependencies) {
                findAllChildren(collector, transitiveDep);
            }

        } catch (Exception e) {
            System.err.println("Warning: Failed to parse POM file for " + dependency + ": " + e.getMessage());
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
        return "POM XML Scanner (supports Maven POM files)";
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

            String content = Files.readString(pomFile);
            return content.trim().startsWith("<?xml") && content.contains("<project");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parses a POM file to extract dependencies.
     */
    @NotNull
    private List<Dependency> parsePomFile(@NotNull Path pomFile) throws Exception {
        List<Dependency> dependencies = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        try (InputStream inputStream = Files.newInputStream(pomFile)) {
            Document document = builder.parse(inputStream);

            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList dependencyNodes = (NodeList) xpath.evaluate(
                    "//project/dependencies/dependency",
                    document,
                    XPathConstants.NODESET
            );

            for (int i = 0; i < dependencyNodes.getLength(); i++) {
                Node dependencyNode = dependencyNodes.item(i);

                try {
                    Dependency dep = parseDependencyNode(dependencyNode);
                    if (dep != null && shouldIncludeDependency(dependencyNode)) {
                        dependencies.add(dep);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Skipping invalid dependency entry in " + pomFile + ": " + e.getMessage());
                }
            }
        }

        return dependencies;
    }

    /**
     * Parses a single dependency node.
     */
    @Nullable
    private Dependency parseDependencyNode(@NotNull Node dependencyNode) throws Exception {
        String groupId = getChildNodeText(dependencyNode, "groupId");
        String artifactId = getChildNodeText(dependencyNode, "artifactId");
        String version = getChildNodeText(dependencyNode, "version");

        if (groupId == null || artifactId == null || version == null) {
            return null;
        }

        if (version.startsWith("${") && version.endsWith("}")) {
            System.err.println("Warning: Skipping dependency with unresolved version property: " +
                    groupId + ":" + artifactId + ":" + version);
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

        if ("test".equals(scope) || "provided".equals(scope)) {
            return false;
        }

        if ("true".equals(optional)) {
            return false;
        }

        return true;
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
                return childNode.getTextContent().trim();
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
