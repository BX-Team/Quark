package org.bxteam.example.gradle;

import org.bukkit.plugin.java.JavaPlugin;
import org.bxteam.quark.bukkit.BukkitLibraryManager;

public class QuarkGradleExamplePlugin extends JavaPlugin {
    private BukkitLibraryManager libraryManager;

    @Override
    public void onLoad() {
        libraryManager = new BukkitLibraryManager(this);
        libraryManager.loadFromGradle();
    }
}
