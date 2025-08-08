<div align="center">

### Quark
A lightweight, runtime dependency management system for plugins running on Minecraft server platforms.

![paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg)
![bukkit](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/bukkit_vector.svg)
![bungeecord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/bungeecord_vector.svg)
![velocity](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/velocity_vector.svg)

</div>

## ✨ Features

- **🚀 Runtime Dependency Loading** - Download and load Maven dependencies at runtime without build-time configuration
- **🔄 Transitive Dependency Resolution** - Automatically resolves and loads all required dependencies
- **📦 Package Relocation** - Relocate packages to avoid conflicts with other plugins or server dependencies
- **🔒 Isolated Class Loading** - Load dependencies into isolated class loaders to prevent conflicts
- **⚡ Dependency Optimization** - Built-in filtering and exclusion options to minimize downloads
- **🎯 Platform Specific** - Dedicated implementations for Paper, Bukkit, BungeeCord, and Velocity

## 📦 Supported Platforms

- Bukkit/Spigot
- Paper
- BungeeCord
- Velocity

## 🚀 Get Started

#### ➕ Add our Repository
```kts
maven("https://repo.bxteam.org/releases")
```
```xml
<repository>
    <id>bx-team-releases</id>
    <url>https://repo.bxteam.org/releases</url>
</repository>
```

#### ➕ Add Quark to dependencies
```kts
implementation("org.bxteam.quark:{artifact}:1.0.0")
```
```xml
<dependency>
    <groupId>org.bxteam.quark</groupId>
    <artifactId>{artifact}</artifactId>
    <version>1.0.0</version>
</dependency>
```

> [!IMPORTANT]  
> Replace `{artifact}` with [platform artifact](https://bxteam.org/docs/quark/supported-platforms)

## Documentation and Support

For complete documentation, advanced usage examples, and configuration options, visit our wiki:

[![generic](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/generic_vector.svg)](https://bxteam.org/docs/quark)

For support, join our Discord server:

[![discord-plural](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg)](https://discord.gg/qNyybSSPm5)

## License ![Static Badge](https://img.shields.io/badge/license-GPL_3.0-lightgreen)

Quark is licensed under the GNU General Public License v3.0. You can find the license [here](LICENSE).
