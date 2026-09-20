package org.bxteam.quark.config.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendProviderTest {
    @TempDir
    Path dir;

    @Test
    void usesTheClassPathFirst() {
        Backend junit = new Backend("JUnit", "org.junit.jupiter:junit-jupiter-api:5.13.4", "org.junit.jupiter.api",
                "org.junit.jupiter.api", "org.junit.jupiter.api.Test");

        assertFalse(junit.relocated());
        assertDoesNotThrow(() -> BackendProvider.ensure(junit, dir));
    }

    @Test
    void explainsWhatToAddWithoutQuarkDependency() {
        Backend missing = new Backend("missing-lib", "com.example:missing:1.0", "com.example.missing",
                "my.plugin.libs.missing", "my.plugin.libs.missing.Api");

        assertFalse(BackendProvider.isDependencyModulePresent());
        BackendUnavailableException error = assertThrows(BackendUnavailableException.class, () -> BackendProvider.ensure(missing, dir));

        assertTrue(error.getMessage().contains("missing-lib (com.example:missing:1.0) is not on the class path"), error.getMessage());
        assertTrue(error.getMessage().contains("quark-dependency"), error.getMessage());
        assertTrue(missing.relocated());
    }

    @Test
    void rejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new Backend("x", "only:two", "a", "a", "a.B"));
    }
}
