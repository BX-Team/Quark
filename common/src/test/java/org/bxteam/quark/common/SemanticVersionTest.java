package org.bxteam.quark.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticVersionTest {
    @Test
    void parsesFullVersion() {
        SemanticVersion version = SemanticVersion.parse("v1.2.3-beta.1+build.5");

        assertTrue(version.isSemantic());
        assertEquals(1, version.major());
        assertEquals(2, version.minor());
        assertEquals(3, version.patch());
        assertEquals("beta.1", version.preRelease());
        assertEquals("build.5", version.build());
        assertEquals("v1.2.3-beta.1+build.5", version.raw());
    }

    @Test
    void missingPartsDefaultToZero() {
        assertEquals(SemanticVersion.of(1, 21, 0), SemanticVersion.parse("1.21"));
        assertEquals(SemanticVersion.of(2, 0, 0), SemanticVersion.parse("2"));
    }

    @Test
    void ordersBySemverPrecedence() {
        List<String> expected = List.of(
                "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
                "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.2.0", "1.10.0", "2.0.0");

        List<SemanticVersion> versions = new ArrayList<>();
        expected.forEach(v -> versions.add(SemanticVersion.parse(v)));
        Collections.shuffle(versions);
        Collections.sort(versions);

        assertEquals(expected, versions.stream().map(SemanticVersion::raw).toList());
    }

    @Test
    void ignoresBuildMetadata() {
        SemanticVersion left = SemanticVersion.parse("1.0.0+a");
        SemanticVersion right = SemanticVersion.parse("1.0.0+b");

        assertEquals(0, left.compareTo(right));
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void fallsBackToLexicographicOrder() {
        SemanticVersion version = SemanticVersion.parse("1.2.3.4");

        assertFalse(version.isSemantic());
        assertTrue(SemanticVersion.parse("build-2").isNewerThan(SemanticVersion.parse("build-10")));
        assertNotEquals(SemanticVersion.parse("1.2.3.4"), SemanticVersion.parse("1.2.3.5"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("  "));
        assertThrows(NullPointerException.class, () -> SemanticVersion.parse(null));
    }
}
