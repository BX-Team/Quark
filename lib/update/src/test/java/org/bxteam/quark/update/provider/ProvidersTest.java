package org.bxteam.quark.update.provider;

import org.bxteam.quark.common.SemanticVersion;
import org.bxteam.quark.update.ReleaseInfo;
import org.bxteam.quark.update.UpdateCheckException;
import org.bxteam.quark.update.UpdateProvider;
import org.bxteam.quark.update.internal.Http;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class ProvidersTest {
    private final CannedServer server = new CannedServer();
    private final Http http = new Http(Runnable::run);

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static Optional<ReleaseInfo> get(UpdateProvider provider) throws Exception {
        return provider.latest().get();
    }

    @Test
    void modrinthPicksNewestRelease() throws Exception {
        server.respond("/v2/project/my-plugin/version", """
                [
                  {"id": "beta1", "version_number": "2.1.0-beta.1", "name": "Beta", "version_type": "beta", "date_published": "2025-03-01T00:00:00Z"},
                  {"id": "rel2", "version_number": "2.0.0", "name": "Two", "version_type": "release", "date_published": "2025-02-01T00:00:00Z"},
                  {"id": "rel1", "version_number": "1.0.0", "name": "One", "version_type": "release", "date_published": "2025-01-01T00:00:00Z"}
                ]
                """);

        ReleaseInfo release = get(new ModrinthProvider("my-plugin", false, List.of(), http, server.uri())).orElseThrow();
        assertEquals(SemanticVersion.parse("2.0.0"), release.version());
        assertEquals("Two", release.name());
        assertEquals(URI.create("https://modrinth.com/project/my-plugin/version/rel2"), release.url());
        assertEquals(Instant.parse("2025-02-01T00:00:00Z"), release.publishedAt());

        ReleaseInfo withBetas = get(new ModrinthProvider("my-plugin", true, List.of(), http, server.uri())).orElseThrow();
        assertEquals(SemanticVersion.parse("2.1.0-beta.1"), withBetas.version());
    }

    @Test
    void modrinthFiltersByLoader() throws Exception {
        server.respond("/v2/project/my-plugin/version?loaders=%5B%22paper%22%2C%22spigot%22%5D",
                "[{\"id\": \"p\", \"version_number\": \"1.5.0\", \"version_type\": \"release\", \"date_published\": \"2025-01-01T00:00:00Z\"}]");

        ReleaseInfo release = get(new ModrinthProvider("my-plugin", false, List.of("paper", "spigot"), http, server.uri())).orElseThrow();

        assertEquals(SemanticVersion.parse("1.5.0"), release.version());
    }

    @Test
    void modrinthUnknownProjectIsEmpty() throws Exception {
        assertEquals(Optional.empty(), get(new ModrinthProvider("missing", false, List.of(), http, server.uri())));
    }

    @Test
    void githubReadsLatestRelease() throws Exception {
        server.respond("/repos/BX-Team/Quark/releases/latest", """
                {"tag_name": "v2.0.0", "name": "Quark 2.0.0", "html_url": "https://github.com/BX-Team/Quark/releases/tag/v2.0.0",
                 "published_at": "2025-05-01T12:00:00Z", "draft": false, "prerelease": false}
                """);

        ReleaseInfo release = get(new GitHubProvider("BX-Team", "Quark", http, server.uri())).orElseThrow();

        assertEquals(SemanticVersion.of(2, 0, 0), release.version());
        assertEquals("Quark 2.0.0", release.name());
        assertEquals(URI.create("https://github.com/BX-Team/Quark/releases/tag/v2.0.0"), release.url());
    }

    @Test
    void githubWithoutReleasesIsEmpty() throws Exception {
        assertEquals(Optional.empty(), get(new GitHubProvider("BX-Team", "Nothing", http, server.uri())));
    }

    @Test
    void hangarCombinesVersionAndOwner() throws Exception {
        server.respond("/api/v1/projects/squaremap/latestrelease", "1.3.5\n")
                .respond("/api/v1/projects/squaremap", "{\"name\": \"squaremap\", \"namespace\": {\"owner\": \"jmp\", \"slug\": \"squaremap\"}}");

        ReleaseInfo release = get(new HangarProvider("squaremap", http, server.uri())).orElseThrow();

        assertEquals(SemanticVersion.parse("1.3.5"), release.version());
        assertEquals(server.uri().resolve("jmp/squaremap/versions/1.3.5"), release.url());
    }

    @Test
    void hangarKeepsVersionWhenProjectLookupFails() throws Exception {
        server.respond("/api/v1/projects/squaremap/latestrelease", "1.3.5")
                .respond("/api/v1/projects/squaremap", 500, "boom");

        ReleaseInfo release = get(new HangarProvider("squaremap", http, server.uri())).orElseThrow();

        assertEquals(SemanticVersion.parse("1.3.5"), release.version());
        assertNull(release.url());
    }

    @Test
    void spigotReadsCurrentVersion() throws Exception {
        server.respond("/simple/0.2/index.php?action=getResource&id=12345", "{\"id\": 12345, \"title\": \"My Plugin\", \"current_version\": \"3.2\"}");

        ReleaseInfo release = get(new SpigotProvider(12345, http, server.uri())).orElseThrow();

        assertEquals(SemanticVersion.of(3, 2, 0), release.version());
        assertEquals("My Plugin", release.name());
        assertEquals(URI.create("https://www.spigotmc.org/resources/12345/"), release.url());
    }

    @Test
    void spigotErrorFails() {
        server.respond("/simple/0.2/index.php?action=getResource&id=1", "{\"error\": \"resource not found\"}");

        ExecutionException e = assertThrows(ExecutionException.class, () -> get(new SpigotProvider(1, http, server.uri())));
        assertInstanceOf(UpdateCheckException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("resource not found"));
    }

    @Test
    void jsonUrlFollowsPath() throws Exception {
        server.respond("/api/maven/latest/version/releases/org/bxteam/ndailyrewards", "{\"version\": \"1.4.0\"}");
        URI url = server.uri().resolve("api/maven/latest/version/releases/org/bxteam/ndailyrewards");
        URI page = URI.create("https://modrinth.com/plugin/ndailyrewards");

        ReleaseInfo release = get(new JsonUrlProvider(url, "version", page, http)).orElseThrow();

        assertEquals(SemanticVersion.parse("1.4.0"), release.version());
        assertEquals(page, release.url());
        assertEquals(Optional.empty(), get(new JsonUrlProvider(url, "missing", null, http)));
    }

    @Test
    void reposiliteReadsLatestVersion() throws Exception {
        server.respond("/api/maven/latest/version/releases/org/bxteam/ndailyrewards", "{\"isSnapshot\": false, \"version\": \"1.4.0\"}");

        ReleaseInfo release = get(new ReposiliteProvider(server.uri(), "releases", "org.bxteam", "ndailyrewards", null, http)).orElseThrow();

        assertEquals(SemanticVersion.parse("1.4.0"), release.version());
    }

    @Test
    void serverErrorsFail() {
        server.respond("/v2/project/broken/version", 503, "maintenance");

        ExecutionException e = assertThrows(ExecutionException.class, () -> get(new ModrinthProvider("broken", false, List.of(), http, server.uri())));
        assertTrue(e.getCause().getMessage().contains("503"));
    }

    @Test
    void firstOfSkipsFailingAndEmptyProviders() throws Exception {
        ReleaseInfo release = new ReleaseInfo(SemanticVersion.parse("1.0.0"), null, null, null);
        UpdateProvider failing = () -> CompletableFuture.failedFuture(new UpdateCheckException("down"));
        UpdateProvider empty = () -> CompletableFuture.completedFuture(Optional.empty());
        UpdateProvider found = () -> CompletableFuture.completedFuture(Optional.of(release));

        assertEquals(Optional.of(release), get(new FirstOfProvider(List.of(failing, empty, found))));
        assertEquals(Optional.empty(), get(new FirstOfProvider(List.of(failing, empty))));

        ExecutionException e = assertThrows(ExecutionException.class, () -> get(new FirstOfProvider(List.of(failing, failing))));
        assertEquals(2, e.getCause().getSuppressed().length);
    }
}
