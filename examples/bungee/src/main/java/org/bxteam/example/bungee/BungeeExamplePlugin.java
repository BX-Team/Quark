package org.bxteam.example.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import org.bxteam.quark.bungee.BungeeLibraryManager;

public class BungeeExamplePlugin extends Plugin {
    private BungeeLibraryManager libraryManager;

    @Override
    public void onLoad() {
        libraryManager = new BungeeLibraryManager(this);
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
