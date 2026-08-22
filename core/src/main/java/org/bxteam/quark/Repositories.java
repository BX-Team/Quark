package org.bxteam.quark;

import lombok.experimental.UtilityClass;

/**
 * A utility class that provides constants for commonly used Maven repository URLs.
 *
 * @apiNote This class cannot be instantiated.
 */
@UtilityClass
public class Repositories {
    /**
     * Maven Central repository URL.
     *
     * @deprecated Use {@link #GOOGLE_MAVEN_CENTRAL_MIRROR} instead to comply with Maven Central Terms of Service.
     */
    @Deprecated
    public final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";

    /**
     * Google Maven Central mirror URL.
     */
    public final String GOOGLE_MAVEN_CENTRAL_MIRROR = "https://maven-central.storage-download.googleapis.com/maven2/";

    /**
     * Sonatype OSS repository URL.
     */
    public final String SONATYPE = "https://oss.sonatype.org/content/groups/public/";

    /**
     * JitPack repository URL.
     */
    public final String JITPACK = "https://jitpack.io/";
}
