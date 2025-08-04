package org.bxteam.quark.bukkit.test;

import org.bxteam.quark.bukkit.BukkitLibraryManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitTestPlugin extends JavaPlugin {
    private BukkitLibraryManager libraryManager;

    @Override
    public void onLoad() {
        libraryManager = new BukkitLibraryManager(this);
        libraryManager.addGoogleMavenCentralMirror();

        libraryManager.loadDependency("com.google.code.gson", "gson", "2.10.1");
    }

    @Override
    public void onDisable() {
        if (libraryManager != null) {
            libraryManager.close();
        }
    }
}
