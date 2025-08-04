package org.bxteam.quark.paper.test;

import org.bukkit.plugin.java.JavaPlugin;
import org.bxteam.quark.paper.PaperLibraryManager;

public class PaperTestPlugin extends JavaPlugin {
    private PaperLibraryManager libraryManager;

    @Override
    public void onLoad() {
        libraryManager = new PaperLibraryManager(this);
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
