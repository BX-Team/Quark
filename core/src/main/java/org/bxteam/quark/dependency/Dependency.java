package org.bxteam.quark.dependency;

import org.bxteam.quark.repository.Repository;
import org.bxteam.quark.resource.ResourceLocator;
import org.bxteam.quark.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Represents a Maven dependency with group ID, artifact ID, and version.
 *
 * <p>This class encapsulates a Maven dependency coordinate and provides
 * utilities for working with semantic versioning, Maven paths, and
 * dependency management features like BOM (Bill of Materials).</p>
 */
public final class Dependency {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "(?<major>[0-9]+)\\.(?<minor>[0-9]+)\\.?(?<patch>[0-9]*)(-(?<label>[-+.a-zA-Z0-9]+))?"
    );

    private static final String PATH_FORMAT = "%s/%s/%s/%s/%s";
    private static final String JAR_MAVEN_FORMAT = "%s-%s.jar";
    private static final String JAR_MAVEN_FORMAT_WITH_CLASSIFIER = "%s-%s-%s.jar";
    private static final String POM_XML_FORMAT = "%s-%s.pom";

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final boolean isBom;
    private final String classifier;

    /**
     * Creates a new dependency.
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param version the dependency version
     */
    private Dependency(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        this(groupId, artifactId, version, false, null);
    }

    /**
     * Creates a new dependency with BOM flag and classifier.
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param version the dependency version
     * @param isBom whether this is a BOM dependency
     * @param classifier the artifact classifier, may be null
     */
    private Dependency(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                       boolean isBom, @Nullable String classifier) {
        this.groupId = requireNonNull(groupId, "Group ID cannot be null");
        this.artifactId = requireNonNull(artifactId, "Artifact ID cannot be null");
        this.version = requireNonNull(version, "Version cannot be null");
        this.isBom = isBom;
        this.classifier = classifier;

        validateVersion(version);
    }

    /**
     * Creates a new dependency from Maven coordinates.
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param version the dependency version
     * @return a new Dependency instance
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if version contains unresolved properties
     */
    @NotNull
    public static Dependency of(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        String sanitizedGroupId = StringUtils.sanitizePath(requireNonNull(groupId, "Group ID cannot be null"));
        String sanitizedArtifactId = StringUtils.sanitizePath(requireNonNull(artifactId, "Artifact ID cannot be null"));
        String sanitizedVersion = StringUtils.sanitizePath(requireNonNull(version, "Version cannot be null"));

        return new Dependency(sanitizedGroupId, sanitizedArtifactId, sanitizedVersion);
    }

    /**
     * Creates a dependency with a classifier.
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param version the dependency version
     * @param classifier the artifact classifier
     * @return a new Dependency instance
     * @throws NullPointerException if any required parameter is null
     */
    @NotNull
    public static Dependency of(@NotNull String groupId, @NotNull String artifactId,
                                @NotNull String version, @Nullable String classifier) {
        String sanitizedGroupId = StringUtils.sanitizePath(requireNonNull(groupId, "Group ID cannot be null"));
        String sanitizedArtifactId = StringUtils.sanitizePath(requireNonNull(artifactId, "Artifact ID cannot be null"));
        String sanitizedVersion = StringUtils.sanitizePath(requireNonNull(version, "Version cannot be null"));

        return new Dependency(sanitizedGroupId, sanitizedArtifactId, sanitizedVersion, false, classifier);
    }

    /**
     * Gets the Maven group ID.
     *
     * @return the group ID
     */
    @NotNull
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the Maven artifact ID.
     *
     * @return the artifact ID
     */
    @NotNull
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Gets the dependency version.
     *
     * @return the version
     */
    @NotNull
    public String getVersion() {
        return version;
    }

    /**
     * Gets the artifact classifier.
     *
     * @return the classifier or null if none
     */
    @Nullable
    public String getClassifier() {
        return classifier;
    }

    /**
     * Gets the group and artifact ID in the format "groupId:artifactId".
     *
     * @return the group artifact ID
     */
    @NotNull
    public String getGroupArtifactId() {
        return groupId + ":" + artifactId;
    }

    /**
     * Gets the full Maven coordinates string.
     *
     * @return the coordinates in "groupId:artifactId:version" format
     */
    @NotNull
    public String getCoordinates() {
        StringBuilder coords = new StringBuilder(groupId).append(':').append(artifactId).append(':').append(version);
        if (classifier != null) {
            coords.append(':').append(classifier);
        }
        return coords.toString();
    }

    /**
     * Checks if this dependency is a BOM (Bill of Materials).
     *
     * @return true if this is a BOM dependency
     */
    public boolean isBom() {
        return isBom;
    }

    /**
     * Checks if this dependency has a classifier.
     *
     * @return true if classifier is present
     */
    public boolean hasClassifier() {
        return classifier != null && !classifier.trim().isEmpty();
    }

    /**
     * Creates a resource locator for the JAR file in the given repository.
     *
     * @param repository the repository to resolve from
     * @return a resource locator for the JAR
     * @throws NullPointerException if repository is null
     */
    @NotNull
    public ResourceLocator toMavenJar(@NotNull Repository repository) {
        String jarName = hasClassifier()
                ? String.format(JAR_MAVEN_FORMAT_WITH_CLASSIFIER, artifactId, version, classifier)
                : String.format(JAR_MAVEN_FORMAT, artifactId, version);

        return toResource(repository, jarName);
    }

    /**
     * Creates a resource locator for a JAR with a specific classifier.
     *
     * @param repository the repository to resolve from
     * @param classifier the classifier to use
     * @return a resource locator for the classified JAR
     * @throws NullPointerException if any parameter is null
     */
    @NotNull
    public ResourceLocator toMavenJar(@NotNull Repository repository, @NotNull String classifier) {
        requireNonNull(classifier, "Classifier cannot be null");
        String jarName = String.format(JAR_MAVEN_FORMAT_WITH_CLASSIFIER, artifactId, version, classifier);
        return toResource(repository, jarName);
    }

    /**
     * Creates a resource locator for the POM file in the given repository.
     *
     * @param repository the repository to resolve from
     * @return a resource locator for the POM
     * @throws NullPointerException if repository is null
     */
    @NotNull
    public ResourceLocator toPomXml(@NotNull Repository repository) {
        String pomName = String.format(POM_XML_FORMAT, artifactId, version);
        return toResource(repository, pomName);
    }

    /**
     * Creates a resource locator for any file in the dependency's directory.
     *
     * @param repository the repository to resolve from
     * @param fileName the file name
     * @return a resource locator for the file
     * @throws NullPointerException if any parameter is null
     */
    @NotNull
    public ResourceLocator toResource(@NotNull Repository repository, @NotNull String fileName) {
        requireNonNull(repository, "Repository cannot be null");
        requireNonNull(fileName, "File name cannot be null");

        String url = String.format(PATH_FORMAT,
                repository.url(),
                groupId.replace(".", "/"),
                artifactId,
                version,
                fileName
        );

        return ResourceLocator.fromURI(url);
    }

    /**
     * Checks if this dependency has a newer version than the given dependency.
     *
     * @param other the dependency to compare against
     * @return true if this dependency is newer
     * @throws NullPointerException if other is null
     * @throws IllegalArgumentException if versions are not in semantic format
     */
    public boolean isNewerThan(@NotNull Dependency other) {
        requireNonNull(other, "Other dependency cannot be null");

        int thisMajor = getMajorVersion();
        int otherMajor = other.getMajorVersion();

        if (thisMajor != otherMajor) {
            return thisMajor > otherMajor;
        }

        int thisMinor = getMinorVersion();
        int otherMinor = other.getMinorVersion();

        if (thisMinor != otherMinor) {
            return thisMinor > otherMinor;
        }

        int thisPatch = getPatchVersion();
        int otherPatch = other.getPatchVersion();

        return thisPatch > otherPatch;
    }

    /**
     * Gets the major version number.
     *
     * @return the major version
     * @throws IllegalArgumentException if version is not in semantic format
     */
    public int getMajorVersion() {
        return getSemanticVersionPart("major");
    }

    /**
     * Gets the minor version number.
     *
     * @return the minor version
     * @throws IllegalArgumentException if version is not in semantic format
     */
    public int getMinorVersion() {
        return getSemanticVersionPart("minor");
    }

    /**
     * Gets the patch version number.
     *
     * @return the patch version
     * @throws IllegalArgumentException if version is not in semantic format
     */
    public int getPatchVersion() {
        return getSemanticVersionPart("patch");
    }

    /**
     * Gets the version label (pre-release identifier).
     *
     * @return the version label or null if none
     * @throws IllegalArgumentException if version is not in semantic format
     */
    @Nullable
    public String getLabelVersion() {
        Matcher matcher = VERSION_PATTERN.matcher(version);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format: " + version + " for dependency: " + this);
        }

        return matcher.group("label");
    }

    /**
     * Extracts a semantic version part by name.
     *
     * @param partName the name of the version part to extract
     * @return the numeric value of the version part
     * @throws IllegalArgumentException if version format is invalid
     */
    private int getSemanticVersionPart(@NotNull String partName) {
        Matcher matcher = VERSION_PATTERN.matcher(version);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format: " + version + " for dependency: " + this);
        }

        String versionPart = matcher.group(partName);

        if (versionPart == null || versionPart.isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(versionPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric version part '" + partName + "': " + versionPart, e);
        }
    }

    /**
     * Creates a copy of this dependency marked as not being a BOM.
     *
     * @return a non-BOM copy of this dependency
     */
    @NotNull
    public Dependency asNotBom() {
        return new Dependency(groupId, artifactId, version, false, classifier);
    }

    /**
     * Creates a copy of this dependency marked as a BOM.
     *
     * @return a BOM copy of this dependency
     */
    @NotNull
    public Dependency asBom() {
        return new Dependency(groupId, artifactId, version, true, classifier);
    }

    /**
     * Validates that the version doesn't contain unresolved property placeholders.
     *
     * @param version the version to validate
     * @throws IllegalArgumentException if version contains unresolved properties
     */
    private static void validateVersion(@NotNull String version) {
        if (version.contains("${")) {
            throw new IllegalArgumentException("Version contains unresolved property placeholder: " + version);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Dependency that = (Dependency) obj;
        return isBom == that.isBom &&
                Objects.equals(groupId, that.groupId) &&
                Objects.equals(artifactId, that.artifactId) &&
                Objects.equals(version, that.version) &&
                Objects.equals(classifier, that.classifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, isBom, classifier);
    }

    @Override
    public String toString() {
        return getCoordinates();
    }
}
