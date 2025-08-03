package org.bxteam.quark;

/**
 * A utility class that provides constants for commonly used Maven repository URLs.
 *
 * @apiNote This class cannot be instantiated.
 */
public final class Repositories {
    private Repositories() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    /**
     * Maven Central repository URL.
     */
    public static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    /**
     * Google Maven Central mirror URL.
     */
    public static final String GOOGLE_MAVEN_CENTRAL_MIRROR = "https://maven-central.storage-download.googleapis.com/maven2/";

    /**
     * Sonatype OSS repository URL.
     */
    public static final String SONATYPE = "https://oss.sonatype.org/content/groups/public/";

    /**
     * JitPack repository URL.
     */
    public static final String JITPACK = "https://jitpack.io/";
}
