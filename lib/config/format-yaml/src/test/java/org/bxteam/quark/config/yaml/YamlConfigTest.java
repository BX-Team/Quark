package org.bxteam.quark.config.yaml;

import org.bxteam.quark.config.ConfigException;
import org.bxteam.quark.config.Migration;
import org.bxteam.quark.config.QuarkConfig;
import org.bxteam.quark.config.validation.ConfigValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlConfigTest {
    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config.yml");
    }

    private TestConfigs.PluginConfig plugin() {
        return QuarkConfig.create(TestConfigs.PluginConfig.class, file());
    }

    @Test
    void createsTheFileWithDefaultsHeaderAndComments() throws IOException {
        plugin().load();

        String text = Files.readString(file());
        assertTrue(text.startsWith("# Test plugin\n\n# Edit with care\n\n# Message prefix\nprefix: '[Test] '\n"), text);
        assertTrue(text.contains("# Players allowed at once\n# 0 = unlimited\nmax-players: 20\n"), text);
        assertTrue(text.contains("cooldown: 5m\n"), text);
        assertTrue(text.contains("database:\n  # Host name\n  host: localhost\n  port: 3306\n  user-name: root\n"), text);
        assertTrue(text.contains("rewards:\n  - item: diamond\n    amount: 2\n"), text);
        assertTrue(text.contains("SERVER_ID: lobby\n"), text);
        assertFalse(text.contains("runtime"), text);
        assertFalse(text.contains("cache"), text);
        assertFalse(Files.exists(dir.resolve("config.yml.tmp")));
    }

    @Test
    void keepsValuesAddsMissingKeysAndRegeneratesInFieldOrder() throws IOException {
        Files.writeString(file(), """
                # my own comment, dropped on save
                database:
                  port: 5432
                max-players: 50
                cooldown: 1h30m
                worlds: [ lobby ]
                limits:
                  hoppers: 4
                owner: 7c0b0d6c-0f4e-4a3e-9b8a-6d3c5f0e2a11
                rewards:
                  - item: gold
                    amount: 64
                default-mode: creative
                """);

        TestConfigs.PluginConfig config = plugin();
        config.load();

        assertEquals(50, config.maxPlayers);
        assertEquals(Duration.ofMinutes(90), config.cooldown);
        assertEquals(List.of("lobby"), config.worlds);
        assertEquals(Map.of("hoppers", 4), config.limits);
        assertEquals(UUID.fromString("7c0b0d6c-0f4e-4a3e-9b8a-6d3c5f0e2a11"), config.owner.orElseThrow());
        assertEquals(List.of(new TestConfigs.Reward("gold", 64)), config.rewards);
        assertEquals(TestConfigs.Mode.CREATIVE, config.defaultMode);
        assertEquals(5432, config.database.port);
        assertEquals("localhost", config.database.host, "missing nested keys keep their defaults");
        assertEquals("runtime", config.runtimeOnly);

        String text = Files.readString(file());
        assertFalse(text.contains("my own comment"), text);
        assertTrue(text.indexOf("prefix:") < text.indexOf("max-players:"), text);
        assertTrue(text.contains("  host: localhost\n  port: 5432\n"), text);
        assertTrue(text.contains("default-mode: CREATIVE\n"), text);
        assertTrue(text.contains("cooldown: 1h30m\n"), text);
    }

    @Test
    void unknownKeysAreKeptAtTheEndOfTheirSection() throws IOException {
        Files.writeString(file(), """
                old-setting: true
                database:
                  pool-size: 8
                  host: db
                """);

        TestConfigs.PluginConfig config = plugin();
        config.load();
        config.save();

        String text = Files.readString(file());
        assertTrue(text.contains("  user-name: root\n  pool-size: 8\n"), text);
        assertTrue(text.endsWith("SERVER_ID: lobby\nold-setting: true\n"), text);
    }

    @Test
    void removeOrphansDropsUnknownKeys() throws IOException {
        Files.writeString(file(), "old-setting: true\ndatabase:\n  pool-size: 8\n");

        QuarkConfig.create(TestConfigs.PluginConfig.class, options -> options.file(file()).removeOrphans()).load();

        String text = Files.readString(file());
        assertFalse(text.contains("old-setting"), text);
        assertFalse(text.contains("pool-size"), text);
    }

    @Test
    void doesNotRewriteAnUpToDateFile() throws IOException {
        plugin().load();
        Files.setLastModifiedTime(file(), java.nio.file.attribute.FileTime.fromMillis(0));

        plugin().load();

        assertEquals(0, Files.getLastModifiedTime(file()).toMillis());
    }

    @Test
    void saveWritesChangedValues() throws IOException {
        TestConfigs.PluginConfig config = plugin();
        config.load();
        config.maxPlayers = 99;
        config.rewards.add(new TestConfigs.Reward("emerald", 1));
        config.save();

        TestConfigs.PluginConfig loaded = plugin();
        loaded.load();
        assertEquals(99, loaded.maxPlayers);
        assertEquals(2, loaded.rewards.size());
    }

    @Test
    void reloadPicksUpEdits() throws IOException {
        TestConfigs.PluginConfig config = plugin();
        config.load();
        TestConfigs.Database database = config.database;

        Files.writeString(file(), Files.readString(file()).replace("max-players: 20", "max-players: 5"));
        config.reload();

        assertEquals(5, config.maxPlayers);
        assertTrue(database != config.database, "nested objects are replaced as a whole");
    }

    @Test
    void invalidValuesFailWithThePathAndKeepTheOldValues() throws IOException {
        TestConfigs.PluginConfig config = plugin();
        config.load();
        String before = Files.readString(file()).replace("max-players: 20", "max-players: lots");
        Files.writeString(file(), before);

        ConfigException error = assertThrows(ConfigException.class, config::reload);

        assertTrue(error.getMessage().contains("max-players"), error.getMessage());
        assertTrue(error.getMessage().contains("'lots' is not a number"), error.getMessage());
        assertEquals(20, config.maxPlayers);
        assertEquals(before, Files.readString(file()), "an invalid file is not overwritten");
    }

    @Test
    void validationFailuresListEveryViolation() throws IOException {
        Files.writeString(file(), "max-players: -1\nprefix: ''\n");
        TestConfigs.PluginConfig config = QuarkConfig.create(TestConfigs.PluginConfig.class, options -> options
                .file(file())
                .validator((field, value) -> field.getName().equals("maxPlayers") && (int) value < 0 ? List.of("must not be negative") : List.of())
                .validator((field, value) -> field.getName().equals("prefix") && ((String) value).isEmpty() ? List.of("must not be empty") : List.of()));

        ConfigValidationException error = assertThrows(ConfigValidationException.class, config::load);

        assertEquals(2, error.violations().size(), error.getMessage());
        assertTrue(error.getMessage().contains("max-players: must not be negative"), error.getMessage());
        assertTrue(error.getMessage().contains("prefix: must not be empty"), error.getMessage());
        assertEquals(20, config.maxPlayers);
        assertEquals("max-players: -1\nprefix: ''\n", Files.readString(file()));
    }

    @Test
    void malformedYamlNamesTheFile() throws IOException {
        Files.writeString(file(), "prefix: [unclosed\n");

        ConfigException error = assertThrows(ConfigException.class, () -> plugin().load());

        assertTrue(error.getMessage().contains("Cannot parse " + file()), error.getMessage());
    }

    @Test
    void migrationsRunStepByStepAndStoreTheVersion() throws IOException {
        Files.writeString(file(), "message: hi\nsize: 2\n");

        TestConfigs.VersionedConfig config = QuarkConfig.create(TestConfigs.VersionedConfig.class, options -> options
                .file(file())
                .migrations(
                        Migration.of(2, 3, root -> root.node("size").set(root.node("size").getInt(0) * 10)),
                        Migration.of(1, 2, root -> root.node("message").moveTo(root.node("greeting")))));
        config.load();

        assertEquals("hi", config.greeting);
        assertEquals(20, config.size);
        assertEquals("config-version: 3\ngreeting: hi\nsize: 20\n", Files.readString(file()));
    }

    @Test
    void newFilesStartAtTheCurrentVersion() throws IOException {
        QuarkConfig.create(TestConfigs.VersionedConfig.class, file()).load();

        assertTrue(Files.readString(file()).startsWith("config-version: 3\n"));
    }

    @Test
    void newerOrUnreachableVersionsFail() throws IOException {
        Files.writeString(file(), "config-version: 4\n");
        ConfigException newer = assertThrows(ConfigException.class, () -> QuarkConfig.create(TestConfigs.VersionedConfig.class, file()).load());
        assertTrue(newer.getMessage().contains("has version 4"), newer.getMessage());

        Files.writeString(file(), "config-version: 2\n");
        ConfigException missing = assertThrows(ConfigException.class, () -> QuarkConfig.create(TestConfigs.VersionedConfig.class, file()).load());
        assertTrue(missing.getMessage().contains("No migration of config.yml from version 2"), missing.getMessage());
        assertEquals("config-version: 2\n", Files.readString(file()));
    }
}
