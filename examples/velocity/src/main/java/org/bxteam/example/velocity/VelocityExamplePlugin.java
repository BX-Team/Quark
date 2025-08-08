package org.bxteam.example.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.bxteam.quark.velocity.VelocityLibraryManager;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "velocityexampleplugin",
        name = "VelocityExamplePlugin",
        version = "1.0.0",
        description = "Example plugin showing work of Quark",
        authors = {"BX Team"}
)
public class VelocityExamplePlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final PluginManager pluginManager;

    private VelocityLibraryManager<VelocityExamplePlugin> libraryManager;

    @Inject
    public VelocityExamplePlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, PluginManager pluginManager) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.pluginManager = pluginManager;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        libraryManager = new VelocityLibraryManager<>(this, logger, dataDirectory, pluginManager);
        libraryManager.addGoogleMavenCentralMirror();

        libraryManager.loadDependency("com.google.code.gson", "gson", "2.10.1");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (libraryManager != null) {
            libraryManager.close();
        }
    }
}
