package org.bxteam.quark.manifest;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.relocation.Relocation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DependencyManifestTest {
    private static final String MANIFEST = """
            # generated
            format=1
            generator=quark-gradle-plugin

            [repositories]
            https://repo.example.org/maven

            [dependencies]
            com.google.code.gson:gson:2.11.0
            org.example:lib:1.0:all

            [relocations]
            com.google.gson=my.plugin.libs.gson

            [future-section]
            anything goes here
            """;

    @Test
    void parsesAllSections() {
        DependencyManifest manifest = DependencyManifest.parse(MANIFEST);

        assertEquals(List.of("https://repo.example.org/maven"), manifest.repositories());
        assertEquals(List.of(
                Dependency.of("com.google.code.gson", "gson", "2.11.0"),
                Dependency.fromCoordinates("org.example:lib:1.0:all")), manifest.dependencies());
        assertEquals(List.of(Relocation.of("com.google.gson", "my.plugin.libs.gson")), manifest.relocations());
    }

    @Test
    void writeAndParseRoundTrip() {
        DependencyManifest manifest = DependencyManifest.parse(MANIFEST);

        assertEquals(manifest, DependencyManifest.parse(manifest.write()));
    }

    @Test
    void requiresFormat() {
        ManifestException e = assertThrows(ManifestException.class, () -> DependencyManifest.parse("[dependencies]\na:b:1\n"));
        assertTrue(e.getMessage().contains("format"));
    }

    @Test
    void rejectsNewerFormat() {
        ManifestException e = assertThrows(ManifestException.class, () -> DependencyManifest.parse("format=99\n"));
        assertTrue(e.getMessage().contains("newer"));
    }

    @Test
    void rejectsMalformedEntries() {
        assertThrows(ManifestException.class, () -> DependencyManifest.parse("format=1\n[dependencies]\nnot-coordinates\n"));
        assertThrows(ManifestException.class, () -> DependencyManifest.parse("format=1\n[relocations]\nno-separator\n"));
    }

    @Test
    void loadReturnsEmptyWithoutManifest() {
        assertEquals(Optional.empty(), DependencyManifest.load(path -> null));
    }

    @Test
    void loadReadsManifestLocation() {
        Optional<DependencyManifest> manifest = DependencyManifest.load(path -> DependencyManifest.LOCATION.equals(path)
                ? new ByteArrayInputStream(MANIFEST.getBytes(StandardCharsets.UTF_8))
                : null);

        assertTrue(manifest.isPresent());
        assertEquals(2, manifest.get().dependencies().size());
    }
}
