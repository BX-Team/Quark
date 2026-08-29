package org.bxteam.quark.pom;

import org.bxteam.quark.pom.exception.MetadataParsingException;
import org.bxteam.quark.pom.model.MavenMetadata;
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
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Reads and parses Maven metadata files (maven-metadata.xml).
 *
 * <p>This class provides functionality to read Maven repository metadata files
 * that contain information about available versions, latest releases, and other
 * artifact metadata. The metadata is typically found at the artifact level in
 * Maven repositories and follows the standard Maven metadata format.</p>
 */
public class MetadataReader {
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;

    /**
     * Creates a new MetadataReader with default XML parser configuration.
     */
    public MetadataReader() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(false);
        this.documentBuilderFactory.setValidating(false);

        this.xPathFactory = XPathFactory.newInstance();
    }

    /**
     * Reads Maven metadata from an input stream.
     *
     * @param inputStream the input stream containing the metadata XML
     * @param source the source description for error reporting (e.g., URL or file path)
     * @return the parsed Maven metadata information
     * @throws MetadataParsingException if parsing fails
     * @throws NullPointerException if any parameter is null
     */
    @NotNull
    public MavenMetadata readMetadata(@NotNull InputStream inputStream, @NotNull String source) throws MetadataParsingException {
        requireNonNull(inputStream, "Input stream cannot be null");
        requireNonNull(source, "Source cannot be null");

        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(inputStream);

            return parseMetadataDocument(document, source);

        } catch (ParserConfigurationException e) {
            throw new MetadataParsingException("Failed to create XML parser", e);
        } catch (SAXException e) {
            throw new MetadataParsingException("Failed to parse metadata XML from " + source, e);
        } catch (IOException e) {
            throw new MetadataParsingException("Failed to read metadata from " + source, e);
        }
    }

    /**
     * Parses a Maven metadata XML document.
     *
     * @param document the XML document to parse
     * @param source the source description for error reporting (e.g., URL or file path)
     * @return the parsed Maven metadata information
     * @throws MetadataParsingException if parsing fails
     */
    @NotNull
    private MavenMetadata parseMetadataDocument(@NotNull Document document, @NotNull String source) throws MetadataParsingException {
        XPath xpath = xPathFactory.newXPath();

        try {
            String groupId = getTextContent(xpath, document, "/metadata/groupId");
            String artifactId = getTextContent(xpath, document, "/metadata/artifactId");
            String latest = getTextContent(xpath, document, "/metadata/versioning/latest");
            String release = getTextContent(xpath, document, "/metadata/versioning/release");

            List<String> versions = extractVersions(xpath, document);

            return new MavenMetadata(groupId, artifactId, latest, release, versions);

        } catch (XPathExpressionException e) {
            throw new MetadataParsingException("Failed to evaluate XPath expression", e);
        }
    }

    /**
     * Extracts all versions from the Maven metadata document.
     *
     * @param xpath the XPath instance for evaluating expressions
     * @param document the XML document containing Maven metadata
     * @return a list of version strings found in the metadata
     * @throws XPathExpressionException if XPath evaluation fails
     */
    @NotNull
    private List<String> extractVersions(@NotNull XPath xpath, @NotNull Document document) throws XPathExpressionException {
        List<String> versions = new ArrayList<>();

        NodeList versionNodes = (NodeList) xpath.evaluate("/metadata/versioning/versions/version", document, XPathConstants.NODESET);

        for (int i = 0; i < versionNodes.getLength(); i++) {
            Node versionNode = versionNodes.item(i);
            String version = versionNode.getTextContent();
            if (version != null && !version.trim().isEmpty()) {
                versions.add(version.trim());
            }
        }

        return versions;
    }

    @Nullable
    private String getTextContent(@NotNull XPath xpath, @NotNull Document document, @NotNull String expression) throws XPathExpressionException {
        Node node = (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
        return node != null ? node.getTextContent().trim() : null;
    }
}
