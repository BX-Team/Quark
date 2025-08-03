package org.bxteam.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;
import org.bxteam.quark.bukkit.BukkitLibraryManager;

public class TestPlugin extends JavaPlugin {
    private BukkitLibraryManager libraryManager;

    @Override
    public void onLoad() {
        libraryManager = new BukkitLibraryManager(this);
        libraryManager.addMavenCentral();

        libraryManager.loadDependency("com.google.code.gson", "gson", "2.10.1");
    }

    @Override
    public void onEnable() {
        Gson gson = new Gson();

        TestData data = new TestData("Hello", "World", 42);
        String json = gson.toJson(data);
        getLogger().info("Serialized JSON: " + json);

        TestData deserializedData = gson.fromJson(json, TestData.class);
        getLogger().info("Deserialized: " + deserializedData);

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("plugin", getName());
        jsonObject.addProperty("version", getDescription().getVersion());
        jsonObject.addProperty("loaded", true);

        getLogger().info("Plugin info JSON: " + gson.toJson(jsonObject));
    }

    @Override
    public void onDisable() {
        if (libraryManager != null) libraryManager.close();
    }

    public static class TestData {
        private final String hello;
        private final String world;
        private final int number;

        public TestData(String hello, String world, int number) {
            this.hello = hello;
            this.world = world;
            this.number = number;
        }

        @Override
        public String toString() {
            return "TestData{hello='" + hello + "', world='" + world + "', number=" + number + "}";
        }
    }
}
