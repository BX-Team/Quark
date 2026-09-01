package org.bxteam.quark.dependency;

import org.bxteam.quark.dependency.model.ResolutionResult;
import org.bxteam.quark.dependency.model.ResolvedDependency;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.repository.Repository;
import org.bxteam.quark.testing.FakeMavenRepository;
import org.bxteam.quark.testing.NoopLogAdapter;
import org.bxteam.quark.testing.PomBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DependencyResolverTest {
    @TempDir
    Path temp;

    private FakeMavenRepository repository;
    private Path localRepository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new FakeMavenRepository(Files.createDirectory(temp.resolve("remote")));
        localRepository = Files.createDirectory(temp.resolve("local"));
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    private ResolutionResult resolve(UnaryOperator<DependencyResolver.Builder> configure, String... coordinates) {
        DependencyResolver.Builder builder = new DependencyResolver.Builder(
                new Logger(new NoopLogAdapter()), List.of(Repository.of(repository.url())), localRepository);
        List<Dependency> roots = List.of(coordinates).stream().map(Dependency::fromCoordinates).toList();
        return configure.apply(builder).build().resolveDependencies(roots);
    }

    private ResolutionResult resolve(String... coordinates) {
        return resolve(UnaryOperator.identity(), coordinates);
    }

    private static Set<String> coordinates(ResolutionResult result) {
        return result.resolvedDependencies().stream()
                .map(resolved -> resolved.dependency().toShortString())
                .collect(Collectors.toSet());
    }

    @Test
    void resolvesTransitiveDependencies() {
        repository.publish("org.example:a:1.0", new PomBuilder().dependency("org.example:b:2.0"))
                .publish("org.example:b:2.0", new PomBuilder().dependency("org.example:c:3.0", "runtime", false))
                .publish("org.example:c:3.0");

        ResolutionResult result = resolve("org.example:a:1.0");

        assertFalse(result.hasErrors(), () -> result.errors().toString());
        assertEquals(Set.of("org.example:a:1.0", "org.example:b:2.0", "org.example:c:3.0"), coordinates(result));
        for (ResolvedDependency resolved : result.resolvedDependencies()) {
            assertTrue(Files.isRegularFile(resolved.jarPath()), resolved::toString);
            assertTrue(resolved.jarPath().startsWith(localRepository), resolved::toString);
        }
    }

    @Test
    void skipsTestProvidedAndOptionalDependencies() {
        repository.publish("org.example:a:1.0", new PomBuilder()
                        .dependency("org.example:tests:1.0", "test", false)
                        .dependency("org.example:provided:1.0", "provided", false)
                        .dependency("org.example:optional:1.0", null, true))
                .publish("org.example:tests:1.0")
                .publish("org.example:provided:1.0")
                .publish("org.example:optional:1.0");

        assertEquals(Set.of("org.example:a:1.0"), coordinates(resolve("org.example:a:1.0")));
    }

    @Test
    void resolvesPropertiesAndManagedVersions() {
        repository.publish("org.example:a:1.0", new PomBuilder()
                        .property("b.version", "2.5")
                        .managed("org.example:c:3.1")
                        .dependency("org.example:b:${b.version}")
                        .dependency("org.example:c"))
                .publish("org.example:b:2.5")
                .publish("org.example:c:3.1");

        assertEquals(Set.of("org.example:a:1.0", "org.example:b:2.5", "org.example:c:3.1"), coordinates(resolve("org.example:a:1.0")));
    }

    @Test
    void doesNotTreatManagedDependenciesAsDependencies() {
        repository.publish("org.example:a:1.0", new PomBuilder()
                        .managed("org.example:unused:9.9")
                        .dependency("org.example:b:1.0"))
                .publish("org.example:b:1.0")
                .publish("org.example:unused:9.9");

        assertEquals(Set.of("org.example:a:1.0", "org.example:b:1.0"), coordinates(resolve("org.example:a:1.0")));
    }

    @Test
    void inheritsPropertiesAndManagementFromParent() {
        repository.publish("org.example:parent:1.0", new PomBuilder()
                        .property("b.version", "4.2")
                        .managed("org.example:c:5.0"))
                .publish("org.example:a:1.0", new PomBuilder()
                        .parent("org.example:parent:1.0")
                        .dependency("org.example:b:${b.version}")
                        .dependency("org.example:c"))
                .publish("org.example:b:4.2")
                .publish("org.example:c:5.0");

        assertEquals(Set.of("org.example:a:1.0", "org.example:b:4.2", "org.example:c:5.0"), coordinates(resolve("org.example:a:1.0")));
    }

    @Test
    void nearerDeclarationsOverrideInheritedOnes() {
        repository.publish("org.example:grandparent:1.0", new PomBuilder()
                        .property("b.version", "1.0")
                        .managed("org.example:c:1.0"))
                .publish("org.example:parent:1.0", new PomBuilder()
                        .parent("org.example:grandparent:1.0")
                        .property("b.version", "2.0"))
                .publish("org.example:a:1.0", new PomBuilder()
                        .parent("org.example:parent:1.0")
                        .managed("org.example:c:3.0")
                        .dependency("org.example:b:${b.version}")
                        .dependency("org.example:c"))
                .publish("org.example:b:2.0")
                .publish("org.example:c:3.0");

        assertEquals(Set.of("org.example:a:1.0", "org.example:b:2.0", "org.example:c:3.0"), coordinates(resolve("org.example:a:1.0")));
    }

    @Test
    void respectsMaxTransitiveDepth() {
        repository.publish("org.example:a:1.0", new PomBuilder().dependency("org.example:b:1.0"))
                .publish("org.example:b:1.0", new PomBuilder().dependency("org.example:c:1.0"))
                .publish("org.example:c:1.0");

        ResolutionResult result = resolve(builder -> builder.maxTransitiveDepth(1), "org.example:a:1.0");

        assertEquals(Set.of("org.example:a:1.0", "org.example:b:1.0"), coordinates(result));
    }

    @Test
    void excludesGroupsAndArtifacts() {
        repository.publish("org.example:a:1.0", new PomBuilder()
                        .dependency("org.excluded:x:1.0")
                        .dependency("org.example:skip-me:1.0")
                        .dependency("org.example:b:1.0"))
                .publish("org.excluded:x:1.0")
                .publish("org.example:skip-me:1.0")
                .publish("org.example:b:1.0");

        ResolutionResult result = resolve(builder -> builder
                .excludeGroupIds("org.excluded")
                .excludeArtifacts("org.example:skip-*"), "org.example:a:1.0");

        assertEquals(Set.of("org.example:a:1.0", "org.example:b:1.0"), coordinates(result));
    }

    @Test
    void reportsMissingArtifactsWithoutThrowing() {
        repository.publish("org.example:a:1.0", new PomBuilder().dependency("org.example:missing:1.0"));

        ResolutionResult result = resolve("org.example:a:1.0");

        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("org.example:missing:1.0")), () -> result.errors().toString());
        assertEquals(Set.of("org.example:a:1.0"), coordinates(result));
    }

    @Test
    void usesFallbackRepository() throws Exception {
        try (FakeMavenRepository fallback = new FakeMavenRepository(Files.createDirectory(temp.resolve("fallback")))) {
            fallback.publish("org.example:only-in-fallback:1.0");
            Dependency dependency = Dependency.fromCoordinates("org.example:only-in-fallback:1.0").withFallbackRepository(fallback.url());

            ResolutionResult result = new DependencyResolver.Builder(new Logger(new NoopLogAdapter()),
                    List.of(Repository.of(repository.url())), localRepository).build().resolveDependencies(List.of(dependency));

            assertFalse(result.hasErrors(), () -> result.errors().toString());
            assertEquals(1, result.getDependencyCount());
        }
    }

    @Test
    void reusesDownloadedArtifacts() {
        repository.publish("org.example:a:1.0");

        resolve("org.example:a:1.0");
        int requestsAfterFirstRun = repository.requests().size();
        ResolutionResult second = resolve("org.example:a:1.0");

        assertFalse(second.hasErrors());
        assertEquals(requestsAfterFirstRun, repository.requests().size(), () -> repository.requests().toString());
    }
}
