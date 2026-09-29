package org.bxteam.quark.dependency.resolver;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenVersionsTest {
    @Test
    void ordersVersionsLikeMaven() {
        List<String> expected = List.of(
                "1.0-alpha1", "1.0-beta", "1.0-m2", "1.0-rc1", "1.0-SNAPSHOT", "1.0", "1.0-sp1", "1.0-jre",
                "1.0.1", "1.2", "1.9", "1.10", "1.10.0.1", "2.0.0-rc.2", "2.0.0", "33.3.1-jre");
        List<String> shuffled = new ArrayList<>(expected);
        java.util.Collections.shuffle(shuffled, new java.util.Random(42));

        shuffled.sort(MavenVersions.COMPARATOR);

        assertEquals(expected, shuffled);
    }

    @Test
    void equivalentSpellings() {
        assertEquals(0, MavenVersions.compare("1", "1.0.0"));
        assertEquals(0, MavenVersions.compare("1.0", "1.0-final"));
        assertEquals(0, MavenVersions.compare("1.0.RELEASE", "1.0"));
        assertEquals("2.10.1", MavenVersions.newer("2.8.9", "2.10.1"));
    }
}
