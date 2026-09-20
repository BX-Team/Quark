package org.bxteam.quark.config.yaml;

import org.bxteam.quark.config.QuarkConfig;
import org.bxteam.quark.config.annotation.Comment;
import org.bxteam.quark.config.annotation.ConfigVersion;
import org.bxteam.quark.config.annotation.CustomKey;
import org.bxteam.quark.config.annotation.Exclude;
import org.bxteam.quark.config.annotation.Header;
import org.bxteam.quark.config.annotation.NameStrategy;
import org.bxteam.quark.config.annotation.NameStyle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class TestConfigs {
    private TestConfigs() {
    }

    enum Mode { SURVIVAL, CREATIVE }

    @Header({"Test plugin", "", "Edit with care"})
    @NameStrategy(NameStyle.HYPHEN_CASE)
    static class PluginConfig extends QuarkConfig {
        @Comment("Message prefix")
        public String prefix = "[Test] ";

        @Comment({"Players allowed at once", "0 = unlimited"})
        public int maxPlayers = 20;

        public boolean debug = false;

        public Mode defaultMode = Mode.SURVIVAL;

        public Duration cooldown = Duration.ofMinutes(5);

        public List<String> worlds = new ArrayList<>(List.of("world", "world_nether"));

        public Map<String, Integer> limits = new LinkedHashMap<>(Map.of("chests", 10));

        public Optional<UUID> owner = Optional.empty();

        @Comment("Database connection")
        public Database database = new Database();

        public List<Reward> rewards = new ArrayList<>(List.of(new Reward("diamond", 2)));

        @CustomKey("SERVER_ID")
        public String serverId = "lobby";

        @Exclude
        public String runtimeOnly = "runtime";

        public transient int cache = 7;
    }

    static class Database {
        @Comment("Host name")
        public String host = "localhost";
        public int port = 3306;
        public String userName = "root";
    }

    record Reward(String item, int amount) {
    }

    @ConfigVersion(3)
    static class VersionedConfig extends QuarkConfig {
        public String greeting = "hello";
        public int size = 1;
    }
}
