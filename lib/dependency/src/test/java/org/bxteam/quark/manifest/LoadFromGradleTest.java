package org.bxteam.quark.manifest;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.testing.FakeMavenRepository;
import org.bxteam.quark.testing.NoopLogAdapter;
import org.bxteam.quark.testing.PomBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadFromGradleTest {
    @TempDir
    Path temp;

    private FakeMavenRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new FakeMavenRepository(Files.createDirectory(temp.resolve("remote")));
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void loadsExactlyTheVersionsGradleChose() throws Exception {
        // a asks for gson 2.8.9, but Gradle picked 2.10.1 for the whole graph when the plugin was built
        repository.publish("org.example:a:1.0", new PomBuilder().dependency("org.example:gson:2.8.9"))
                .publish("org.example:gson:2.8.9")
                .publish("org.example:gson:2.10.1");
        String manifest = "format=1\n\n[repositories]\n" + repository.url()
                + "\n\n[dependencies]\norg.example:a:1.0\norg.example:gson:2.10.1\n\n[relocations]\n";

        LibraryManager manager = LibraryManager.builder()
                .dataDirectory(temp.resolve("data"))
                .logAdapter(new NoopLogAdapter())
                .resources(path -> DependencyManifest.LOCATION.equals(path)
                        ? new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)) : null)
                .build();
        try (IsolatedClassLoader classLoader = new IsolatedClassLoader()) {
            manager.loadFromGradle(classLoader);
        }

        Set<String> loaded = manager.getLoadedDependencies().keySet().stream()
                .map(dependency -> dependency.toShortString())
                .collect(Collectors.toSet());
        assertEquals(Set.of("org.example:a:1.0", "org.example:gson:2.10.1"), loaded);
        assertTrue(repository.requests().stream().noneMatch(path -> path.endsWith(".pom")), repository.requests()::toString);
    }
}
