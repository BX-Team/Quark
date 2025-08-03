package org.bxteam.quark.pom;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.DependencyCollector;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * Scanner for Maven POM.xml files to resolve transitive dependencies.
 *
 * <p>This scanner downloads and parses POM files from Maven repositories to build
 * a complete dependency graph. It handles various Maven features including:</p>
 * <ul>
 *   <li>Transitive dependency resolution</li>
 *   <li>Property substitution in versions</li>
 *   <li>Dependency management (BOM) imports</li>
 *   <li>Scope filtering (compile, runtime)</li>
 *   <li>Optional dependency handling</li>
 * </ul>
 */
public class PomXmlScanner implements DependencyScanner {

    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = createSecureDocumentBuilderFactory();

    // Maven scopes that we process
    private static final Set<String> ACCEPTED_SCOPES = Set.of("compile", "runtime", "import");

    private final Repository localRepository;
    private final List<Repository> repositories;

    /**
     * Creates a new POM scanner.
     *
     * @param repositories the repositories to search for POM files
     * @param localRepository the local repository for caching
     * @throws NullPointerException if any parameter is null
     */
    public PomXmlScanner(@NotNull List<Repository> repositories, @NotNull Repository localRepository) {
        this.repositories = new ArrayList<>(requireNonNull(repositories, "Repositories cannot be null"));
        this.localRepository = requireNonNull(localRepository, "Local repository cannot be null");
    }

    /**
     * Creates a secure DocumentBuilderFactory with XXE protection.
     */
    @NotNull
    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            // Enable secure processing
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            // Disable DTD processing to prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            // Disable XInclude and entity expansion
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

        } catch (ParserConfigurationException e) {
            throw new PomScannerException("Failed to configure secure XML parser", e);
        }

        return factory;
    }

    @Override
    @NotNull
    public DependencyCollector findAllChildren(@NotNull DependencyCollector collector, @NotNull Dependency dependency) {
        requireNonNull(collector, "Dependency collector cannot be null");
        requireNonNull(dependency, "Dependency cannot be null");

        for (Repository repository : repositories) {
            Optional<List<Dependency>> childDependencies = tryReadDependencies(dependency, repository);

            if (childDependencies.isEmpty()) {
                continue;
            }

            for (Dependency childDependency : childDependencies.get()) {
                if (collector.hasScannedDependency(childDependency)) {
                    continue;
                }

                if (!childDependency.isBom()) {
                    findAllChildren(collector, childDependency);
                }
            }

            break;
        }

        return collector;
    }

    /**
     * Attempts to read dependencies from a POM file in the given repository.
     */
    @NotNull
    private Optional<List<Dependency>> tryReadDependencies(@NotNull Dependency dependency, @NotNull Repository repository) {
        try {
            File pomFile = downloadPomToLocalRepository(dependency, repository);
            List<Dependency> dependencies = parsePomFile(pomFile);
            return Optional.of(dependencies);

        } catch (IOException | SAXException | ParserConfigurationException | URISyntaxException e) {
            // Log at debug level - this is expected when POM doesn't exist in repository
            return Optional.empty();
        }
    }

    /**
     * Downloads a POM file to the local repository cache.
     */
    @NotNull
    private File downloadPomToLocalRepository(@NotNull Dependency dependency, @NotNull Repository repository)
            throws URISyntaxException, IOException {

        // Create local file path based on dependency coordinates
        File localPomFile = createLocalPomFile(dependency);

        // Return existing file if it's valid
        if (localPomFile.exists() && !isEmptyFile(localPomFile)) {
            return localPomFile;
        }

        // Download from repository
        URL pomUrl = createPomUrl(dependency, repository);
        downloadFile(pomUrl, localPomFile);

        return localPomFile;
    }

    /**
     * Creates the local file path for a POM based on dependency coordinates.
     */
    @NotNull
    private File createLocalPomFile(@NotNull Dependency dependency) throws URISyntaxException {
        String pomPath = dependency.getGroupId().replace('.', '/') + '/' +
                dependency.getArtifactId() + '/' +
                dependency.getVersion() + '/' +
                dependency.getArtifactId() + '-' + dependency.getVersion() + ".pom";

        if (localRepository instanceof org.bxteam.quark.repository.LocalRepository localRepo) {
            return localRepo.resolve(pomPath).toFile();
        } else {
            // Handle generic repository
            return new File(localRepository.getUrl() + "/" + pomPath);
        }
    }

    /**
     * Creates the URL for a POM file in a repository.
     */
    @NotNull
    private URL createPomUrl(@NotNull Dependency dependency, @NotNull Repository repository) throws IOException {
        String pomPath = dependency.getGroupId().replace('.', '/') + '/' +
                dependency.getArtifactId() + '/' +
                dependency.getVersion() + '/' +
                dependency.getArtifactId() + '-' + dependency.getVersion() + ".pom";

        return new URL(repository.getArtifactUrl(pomPath));
    }

    /**
     * Downloads a file from URL to local path.
     */
    private void downloadFile(@NotNull URL url, @NotNull File targetFile) throws IOException {
        // Ensure parent directories exist
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            Files.createDirectories(parentDir.toPath());
        }

        // Download the file
        try (InputStream inputStream = url.openStream()) {
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Checks if a file is empty or effectively empty.
     */
    private boolean isEmptyFile(@NotNull File file) {
        try {
            return Files.size(file.toPath()) == 0;
        } catch (IOException e) {
            return true; // Treat as empty if we can't read it
        }
    }

    /**
     * Parses a POM file and extracts dependencies.
     */
    @NotNull
    private List<Dependency> parsePomFile(@NotNull File pomFile)
            throws ParserConfigurationException, IOException, SAXException {

        DocumentBuilder builder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        Document document = builder.parse(pomFile);

        Element rootElement = document.getDocumentElement();
        PomXmlProperties properties = PomXmlProperties.from(rootElement);

        List<Dependency> allDependencies = new ArrayList<>();

        // Read BOM dependencies first (they might affect regular dependencies)
        allDependencies.addAll(readBomDependencies(rootElement, properties));

        // Read regular dependencies
        allDependencies.addAll(readRegularDependencies(rootElement, properties));

        return Collections.unmodifiableList(allDependencies);
    }

    /**
     * Reads regular dependencies from the dependencies section.
     */
    @NotNull
    private List<Dependency> readRegularDependencies(@NotNull Element root, @NotNull PomXmlProperties properties) {
        Element dependenciesElement = (Element) XmlUtil.getChildNode(root, "dependencies");
        if (dependenciesElement == null) {
            return Collections.emptyList();
        }

        NodeList dependencyNodes = dependenciesElement.getElementsByTagName("dependency");
        List<Dependency> dependencies = new ArrayList<>();

        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependencyElement = (Element) dependencyNodes.item(i);

            Optional<Dependency> dependency = parseDependencyElement(dependencyElement, properties);
            dependency.ifPresent(dependencies::add);
        }

        return dependencies;
    }

    /**
     * Reads BOM dependencies from dependency management section.
     */
    @NotNull
    private List<Dependency> readBomDependencies(@NotNull Element root, @NotNull PomXmlProperties properties) {
        Element dependencyManagementElement = (Element) XmlUtil.getChildNode(root, "dependencyManagement");
        if (dependencyManagementElement == null) {
            return Collections.emptyList();
        }

        Element dependenciesElement = (Element) XmlUtil.getChildNode(dependencyManagementElement, "dependencies");
        if (dependenciesElement == null) {
            return Collections.emptyList();
        }

        NodeList dependencyNodes = dependenciesElement.getElementsByTagName("dependency");
        List<Dependency> bomDependencies = new ArrayList<>();

        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependencyElement = (Element) dependencyNodes.item(i);

            String scope = XmlUtil.getElementContent(dependencyElement, "scope");
            if (!isAcceptedScope(scope)) {
                continue;
            }

            Optional<Dependency> dependency = parseDependencyElement(dependencyElement, properties);
            if (dependency.isEmpty()) {
                continue;
            }

            Dependency bomDependency = dependency.get().asBom();

            // Handle BOM imports
            if ("import".equals(scope)) {
                bomDependencies.addAll(readImportedBomDependencies(bomDependency));
            }

            bomDependencies.add(bomDependency);
        }

        return bomDependencies;
    }

    /**
     * Parses a single dependency element.
     */
    @NotNull
    private Optional<Dependency> parseDependencyElement(@NotNull Element dependencyElement, @NotNull PomXmlProperties properties) {
        String groupId = XmlUtil.getElementContent(dependencyElement, "groupId");
        String artifactId = XmlUtil.getElementContent(dependencyElement, "artifactId");
        String version = XmlUtil.getElementContent(dependencyElement, "version");

        // Skip if required fields are missing
        if (groupId == null || artifactId == null || version == null) {
            return Optional.empty();
        }

        // Check scope
        String scope = XmlUtil.getElementContent(dependencyElement, "scope");
        if (!isAcceptedScope(scope)) {
            return Optional.empty();
        }

        // Skip optional dependencies
        String optional = XmlUtil.getElementContent(dependencyElement, "optional");
        if (XmlUtil.parseBoolean(optional)) {
            return Optional.empty();
        }

        // Resolve properties in version
        String resolvedVersion = properties.resolveProperties(version);
        if (resolvedVersion == null) {
            return Optional.empty(); // Could not resolve version
        }

        return Optional.of(Dependency.of(groupId, artifactId, resolvedVersion));
    }

    /**
     * Reads dependencies from an imported BOM.
     */
    @NotNull
    private List<Dependency> readImportedBomDependencies(@NotNull Dependency bomDependency) {
        for (Repository repository : repositories) {
            Optional<List<Dependency>> bomDependencies = tryReadDependencies(bomDependency, repository);

            if (bomDependencies.isPresent()) {
                return bomDependencies.get().stream()
                        .map(Dependency::asBom)
                        .toList();
            }
        }

        return Collections.emptyList();
    }

    /**
     * Checks if a scope is accepted for processing.
     */
    private static boolean isAcceptedScope(@NotNull String scope) {
        return scope == null || ACCEPTED_SCOPES.contains(scope);
    }

    @Override
    public String toString() {
        return "PomXmlScanner{" +
                "repositories=" + repositories.size() +
                ", localRepository=" + localRepository +
                '}';
    }

    /**
     * Exception thrown when POM scanning operations fail.
     */
    public static class PomScannerException extends RuntimeException {
        public PomScannerException(String message) {
            super(message);
        }

        public PomScannerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
