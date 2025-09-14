package org.bxteam.example.paper;

import org.bukkit.plugin.java.JavaPlugin;
import org.bxteam.quark.paper.PaperLibraryManager;

public class PaperExamplePlugin extends JavaPlugin {
    private PaperLibraryManager libraryManager;

    @Override
    public void onLoad() {
        libraryManager = new PaperLibraryManager(this);
        libraryManager.addGoogleMavenCentralMirror();

        libraryManager.loadDependency("com.google.code.gson", "gson", "2.10.1");
    }
}
