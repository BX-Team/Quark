package org.bxteam.quark.pom;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static java.util.Objects.requireNonNull;

/**
 * Utility class for XML DOM manipulation operations.
 *
 * <p>This class provides helper methods for working with XML DOM elements,
 * particularly for parsing Maven POM.xml files. It includes methods for
 * finding child nodes and extracting element content.</p>
 */
final class XmlUtil {
    private XmlUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    /**
     * Finds a direct child node by name.
     *
     * @param parent the parent element to search in
     * @param nodeName the name of the child node to find
     * @return the child node or null if not found
     * @throws NullPointerException if parent or nodeName is null
     */
    @Nullable
    static Node getChildNode(@NotNull Element parent, @NotNull String nodeName) {
        requireNonNull(parent, "Parent element cannot be null");
        requireNonNull(nodeName, "Node name cannot be null");

        NodeList childNodes = parent.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE && nodeName.equals(node.getNodeName())) {
                return node;
            }
        }

        return null;
    }

    /**
     * Gets the text content of the first child element with the specified tag name.
     *
     * @param parent the parent element to search in
     * @param tagName the tag name to search for
     * @return the text content or null if the element is not found
     * @throws NullPointerException if parent or tagName is null
     */
    @Nullable
    static String getElementContent(@NotNull Element parent, @NotNull String tagName) {
        requireNonNull(parent, "Parent element cannot be null");
        requireNonNull(tagName, "Tag name cannot be null");

        NodeList elements = parent.getElementsByTagName(tagName);

        if (elements.getLength() == 0) {
            return null;
        }

        Node firstElement = elements.item(0);
        if (firstElement == null) {
            return null;
        }

        String textContent = firstElement.getTextContent();
        return (textContent != null) ? textContent.trim() : null;
    }

    /**
     * Gets the text content of the first child element with the specified tag name,
     * with a default value if not found.
     *
     * @param parent the parent element to search in
     * @param tagName the tag name to search for
     * @param defaultValue the default value to return if element is not found
     * @return the text content or the default value
     * @throws NullPointerException if parent or tagName is null
     */
    @NotNull
    static String getElementContent(@NotNull Element parent, @NotNull String tagName, @NotNull String defaultValue) {
        String content = getElementContent(parent, tagName);
        return (content != null) ? content : requireNonNull(defaultValue, "Default value cannot be null");
    }

    /**
     * Checks if an element has a child element with the specified tag name.
     *
     * @param parent the parent element to check
     * @param tagName the tag name to look for
     * @return true if the child element exists
     * @throws NullPointerException if parent or tagName is null
     */
    static boolean hasChildElement(@NotNull Element parent, @NotNull String tagName) {
        requireNonNull(parent, "Parent element cannot be null");
        requireNonNull(tagName, "Tag name cannot be null");

        return parent.getElementsByTagName(tagName).getLength() > 0;
    }

    /**
     * Gets all direct child elements with the specified tag name.
     *
     * @param parent the parent element to search in
     * @param tagName the tag name to search for
     * @return array of matching child elements (never null, but may be empty)
     * @throws NullPointerException if parent or tagName is null
     */
    @NotNull
    static Element[] getChildElements(@NotNull Element parent, @NotNull String tagName) {
        requireNonNull(parent, "Parent element cannot be null");
        requireNonNull(tagName, "Tag name cannot be null");

        NodeList nodeList = parent.getElementsByTagName(tagName);
        Element[] elements = new Element[nodeList.getLength()];

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element) {
                elements[i] = (Element) node;
            }
        }

        return elements;
    }

    /**
     * Checks if a string value represents a boolean true.
     *
     * @param value the string value to check
     * @return true if the value represents a boolean true
     */
    static boolean parseBoolean(@Nullable String value) {
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Safely gets the text content from a node, handling null cases.
     *
     * @param node the node to get content from
     * @return the text content or null if node is null
     */
    @Nullable
    static String getTextContent(@Nullable Node node) {
        if (node == null) {
            return null;
        }

        String content = node.getTextContent();
        return (content != null) ? content.trim() : null;
    }
}
