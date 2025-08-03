package org.bxteam.quark.pom;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Handles Maven POM properties and property resolution.
 *
 * <p>This class parses and manages properties defined in Maven POM files,
 * providing functionality to resolve property placeholders in dependency
 * versions and other configuration values.</p>
 *
 * <p>Example property resolution:</p>
 * <ul>
 *   <li>{@code ${junit.version}} → {@code 5.8.2}</li>
 *   <li>{@code ${project.version}} → {@code 1.0.0}</li>
 * </ul>
 */
class PomXmlProperties {
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Properties properties;
    private final Properties builtInProperties;

    /**
     * Creates a new PomXmlProperties instance.
     *
     * @param properties the properties from the POM file
     */
    private PomXmlProperties(@NotNull Properties properties) {
        this.properties = requireNonNull(properties, "Properties cannot be null");
        this.builtInProperties = createBuiltInProperties();
    }

    /**
     * Creates PomXmlProperties from a POM root element.
     *
     * @param root the root element of the POM document
     * @return a new PomXmlProperties instance
     * @throws NullPointerException if root is null
     */
    @NotNull
    static PomXmlProperties from(@NotNull Element root) {
        requireNonNull(root, "Root element cannot be null");

        Properties properties = new Properties();

        // Add project-level properties
        addProjectProperties(root, properties);

        // Add custom properties from <properties> section
        addCustomProperties(root, properties);

        return new PomXmlProperties(properties);
    }

    /**
     * Adds built-in Maven project properties.
     */
    private static void addProjectProperties(@NotNull Element root, @NotNull Properties properties) {
        // Add project.version
        String version = XmlUtil.getElementContent(root, "version");
        if (version != null) {
            properties.setProperty("project.version", version);
        }

        // Add project.groupId
        String groupId = XmlUtil.getElementContent(root, "groupId");
        if (groupId != null) {
            properties.setProperty("project.groupId", groupId);
        }

        // Add project.artifactId
        String artifactId = XmlUtil.getElementContent(root, "artifactId");
        if (artifactId != null) {
            properties.setProperty("project.artifactId", artifactId);
        }
    }

    /**
     * Adds custom properties from the <properties> section.
     */
    private static void addCustomProperties(@NotNull Element root, @NotNull Properties properties) {
        Node propertiesNode = XmlUtil.getChildNode(root, "properties");
        if (!(propertiesNode instanceof Element propertiesElement)) {
            return;
        }

        NodeList childNodes = propertiesElement.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) node;
            String propertyName = element.getNodeName();
            String propertyValue = XmlUtil.getTextContent(element);

            if (propertyValue != null && !propertyValue.isEmpty()) {
                // Handle range notation like [1.0,2.0)
                if (propertyValue.startsWith("[") && propertyValue.contains(",")) {
                    // Extract the first version from range
                    int commaIndex = propertyValue.indexOf(',');
                    propertyValue = propertyValue.substring(1, commaIndex);
                }

                properties.setProperty(propertyName, propertyValue);
            }
        }
    }

    /**
     * Creates built-in system properties.
     */
    @NotNull
    private Properties createBuiltInProperties() {
        Properties builtIn = new Properties();

        // Add common Maven built-in properties
        builtIn.setProperty("maven.version", "3.8.1"); // Default Maven version

        // Add system properties that are commonly used
        String javaVersion = System.getProperty("java.version");
        if (javaVersion != null) {
            builtIn.setProperty("java.version", javaVersion);
        }

        return builtIn;
    }

    /**
     * Resolves property placeholders in a value string.
     *
     * @param value the value that may contain property placeholders
     * @return the resolved value or null if the value is null or cannot be resolved
     */
    @Nullable
    public String resolveProperties(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        return resolvePropertiesRecursive(value.trim(), 0);
    }

    /**
     * Legacy method name for compatibility.
     *
     * @param value the value to resolve
     * @return the resolved value
     */
    @Nullable
    public String replaceProperties(@Nullable String value) {
        return resolveProperties(value);
    }

    /**
     * Recursively resolves property placeholders with cycle detection.
     */
    @Nullable
    private String resolvePropertiesRecursive(@NotNull String value, int depth) {
        // Prevent infinite recursion
        if (depth > 10) {
            return value;
        }

        Matcher matcher = PROPERTY_PATTERN.matcher(value);
        if (!matcher.find()) {
            return value; // No more properties to resolve
        }

        StringBuffer result = new StringBuffer();
        matcher.reset();

        while (matcher.find()) {
            String propertyKey = matcher.group(1);
            String propertyValue = getPropertyValue(propertyKey);

            if (propertyValue != null) {
                // Recursively resolve the property value
                propertyValue = resolvePropertiesRecursive(propertyValue, depth + 1);
                if (propertyValue == null) {
                    return null; // Could not resolve nested property
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(propertyValue));
            } else {
                // Property not found, return null to indicate failure
                return null;
            }
        }

        matcher.appendTail(result);

        String resolved = result.toString();

        // Check if we need another pass
        if (PROPERTY_PATTERN.matcher(resolved).find()) {
            return resolvePropertiesRecursive(resolved, depth + 1);
        }

        return resolved;
    }

    /**
     * Gets a property value by key, checking both custom and built-in properties.
     */
    @Nullable
    private String getPropertyValue(@NotNull String key) {
        // Check custom properties first
        String value = properties.getProperty(key);
        if (value != null) {
            return value;
        }

        // Check built-in properties
        value = builtInProperties.getProperty(key);
        if (value != null) {
            return value;
        }

        // Check system properties as fallback
        return System.getProperty(key);
    }

    /**
     * Checks if a property is defined.
     *
     * @param key the property key
     * @return true if the property is defined
     */
    public boolean hasProperty(@NotNull String key) {
        requireNonNull(key, "Property key cannot be null");

        return properties.containsKey(key) ||
                builtInProperties.containsKey(key) ||
                System.getProperties().containsKey(key);
    }

    /**
     * Gets all custom properties defined in the POM.
     *
     * @return a copy of the custom properties
     */
    @NotNull
    public Properties getCustomProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    /**
     * Gets the number of custom properties.
     *
     * @return the property count
     */
    public int getPropertyCount() {
        return properties.size();
    }

    @Override
    public String toString() {
        return "PomXmlProperties{" +
                "customProperties=" + properties.size() +
                ", builtInProperties=" + builtInProperties.size() +
                '}';
    }
}
