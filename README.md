<div align="center">

<h1>
<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/branding/logo-dark.svg">
  <img src=".github/branding/logo-light.svg" height="36" align="top" alt="">
</picture>
Quark
</h1>

Quark is a small framework for Minecraft plugin developers. Download Maven dependencies when the plugin starts instead of shading them, keep configs in annotated classes, check for updates and run a test server straight from Gradle. Every module is optional: a plugin ships only what it uses, relocated into its own package.

![paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg)
![bukkit](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/bukkit_vector.svg)
![velocity](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/velocity_vector.svg)
![bungeecord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/bungeecord_vector.svg)

[![Chat on Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/qNyybSSPm5)
[![Read the Docs](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/generic_vector.svg)](https://bxteam.org/docs/quark)
[![github](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg)](https://github.com/BX-Team/Quark)

</div>

## ⚙️ Features

- **📦 Runtime dependencies** — dependencies declared with `quark(...)` are downloaded, resolved transitively and loaded when the plugin starts, so the jar stays small.
- **🔀 Relocation** — Quark and your libraries are relocated into the plugin's package, in the shaded jar and at runtime. `relocate = true` on one dependency keeps the plugin on its own version even when the server ships another.
- **📝 Configs** — plain classes with `@Comment`, `@Header` and versioned migrations, saved as YAML in field order, with validation through `@Min`, `@Max`, `@Pattern` and friends. Serializers for ItemStack, Location, Sound and Adventure components on Bukkit.
- **🔔 Update checks** — Modrinth, Hangar, GitHub Releases, SpigotMC or any JSON endpoint, with a console and join notification.
- **🧵 Platform adapters** — one `Platform` and `Scheduler` API for Paper, Folia, Bukkit, Velocity and BungeeCord.
- **🐘 Gradle plugin** — picks the adapter and modules, writes the dependency manifest and `plugin.yml`, configures Shadow and starts a dev server with your plugin and others from Modrinth, Hangar, GitHub or Jenkins.

## 🚀 Getting started

Apply the Gradle plugin next to Shadow in the project that builds the plugin jar:

```kotlin
import org.bxteam.quark.gradle.QuarkModule
import org.bxteam.quark.gradle.ServerPlatform
import org.bxteam.quark.gradle.relocate

plugins {
    java
    id("com.gradleup.shadow") version "9.1.0"
    id("org.bxteam.quark") version "2.0.0"
}

group = "com.example"

quark {
    platform = ServerPlatform.PAPER
    modules(QuarkModule.DEPENDENCY, QuarkModule.CONFIG, QuarkModule.CONFIG_VALIDATOR)

    pluginYml {
        main = "com.example.MyPlugin"
        apiVersion = "1.20"
    }

    devServer {
        version = "1.21.8"
        acceptEula()
    }
}

dependencies {
    quark("com.zaxxer:HikariCP:7.1.0")
    quark("com.google.code.gson:gson:2.11.0") { relocate = true }
}
```

Load the dependencies before anything uses them, and the config when the plugin enables:

```java
public final class MyPlugin extends JavaPlugin {
    private PluginConfig config;

    @Override
    public void onLoad() {
        LibraryManager.builder()
                .plugin(this)
                .dataDirectory(getDataFolder().toPath())
                .build()
                .loadFromGradle();
    }

    @Override
    public void onEnable() {
        config = QuarkConfig.create(PluginConfig.class, getDataFolder().toPath().resolve("config.yml"));
        config.load();
    }
}

@Header("MyPlugin configuration")
@NameStrategy(NameStyle.HYPHEN_CASE)
public class PluginConfig extends QuarkConfig {
    @Comment("Prefix of every message")
    public String prefix = "<gray>[MyPlugin]</gray> ";

    @Min(1)
    public int maxHomes = 3;
}
```

`./gradlew runServer` then starts Paper 1.21.8 with the plugin in `run/paper`.

| `QuarkModule` | Adds |
| ------------- | ---- |
| `DEPENDENCY` | `quark-dependency` — runtime downloads, relocation, isolated class loaders |
| `CONFIG` | `quark-config`, `quark-config-yaml`, and `quark-config-serdes-bukkit` on Paper, Folia and Bukkit |
| `CONFIG_VALIDATOR` | `quark-config-validator` |
| `LOGGER` | `quark-logger` — SLF4J and Log4j adapters |
| `UPDATE` | `quark-update` |

Everything else — isolated class loaders, update providers, multiple dev servers, migrations — is in the [documentation](https://bxteam.org/docs/quark).

## 🧪 API

Without the Gradle plugin, add the repository and pick modules through the BOM:

```kotlin
repositories {
    maven("https://repo.bxteam.org/releases")
}

dependencies {
    implementation(platform("org.bxteam.quark:quark-bom:2.0.0"))
    implementation("org.bxteam.quark:quark-paper")
    implementation("org.bxteam.quark:quark-config-yaml")
}
```

```xml
<repositories>
    <repository>
        <id>bx-team-releases</id>
        <url>https://repo.bxteam.org/releases</url>
    </repository>
</repositories>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.bxteam.quark</groupId>
            <artifactId>quark-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.bxteam.quark</groupId>
        <artifactId>quark-paper</artifactId>
    </dependency>
    <dependency>
        <groupId>org.bxteam.quark</groupId>
        <artifactId>quark-config-yaml</artifactId>
    </dependency>
</dependencies>
```

Shade Quark and relocate `org.bxteam.quark` into your own package: two plugins with different Quark versions on one server would otherwise load each other's classes.

## 🔨 Build from source

Quark needs JDK 17 or newer to build.

```bash
git clone https://github.com/BX-Team/Quark.git
cd Quark
./gradlew build                 # every module, with tests
./gradlew publishToMavenLocal   # try a change in your own plugin
```

## 🤝 Contributing

Read the [contributing guidelines](https://github.com/BX-Team/.github/blob/master/CONTRIBUTING.md) first. Every pull request runs `./gradlew build --configuration-cache` and `./gradlew checkArtifactSize`, so run both before opening one.

## ⚖️ License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## 💛 Credits

- [lucko/jar-relocator](https://github.com/lucko/jar-relocator): The relocator Quark 1.x used and its own relocator learned from.
- [OkaeriPoland/okaeri-configs](https://github.com/OkaeriPoland/okaeri-configs): The model for annotation-driven configs that are regenerated from the class.
- [SpongePowered/Configurate](https://github.com/SpongePowered/Configurate): The node tree and serializer design behind `ConfigNode`.
- [snakeyaml/snakeyaml-engine](https://bitbucket.org/snakeyaml/snakeyaml-engine): The YAML parser behind `quark-config-yaml`.
- [OW2 ASM](https://asm.ow2.io/): Bytecode rewriting for runtime relocation.
- [mcjars.app](https://mcjars.app): Server builds for the dev servers.
