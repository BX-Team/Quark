package org.bxteam.quark.gradle.pluginyml

import org.bxteam.quark.gradle.ServerPlatform
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * The plugin descriptor, written for `quark.platform`: `plugin.yml` for Bukkit, Paper and Folia
 * (`paper-plugin.yml` with [paperPlugin]), `bungee.yml` for BungeeCord, `velocity-plugin.json` for Velocity.
 *
 * Dependencies are declared once with [depend], [softDepend] and [loadBefore] and translated to each format.
 * Metadata a format has no key for (a website in `bungee.yml`) is left out; a field that changes how the server
 * loads the plugin (commands in `velocity-plugin.json`) fails the build. Dependencies of the `quark` configuration
 * are loaded by Quark, so there is no `libraries` list.
 *
 * ```kotlin
 * pluginYml {
 *     main = "com.example.MyPlugin"
 *     apiVersion = "1.20"
 *     authors("Me")
 *     softDepend("PlaceholderAPI")
 *     commands {
 *         register("hello") { aliases("hi") }
 *     }
 *     permissions {
 *         register("myplugin.reload") { default = OP }
 *     }
 * }
 * ```
 */
abstract class PluginYmlSpec @Inject constructor(objects: ObjectFactory) {
    internal val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /** Plugin name. Defaults to the name of the root project. */
    abstract val name: Property<String>

    /** Velocity plugin id. Defaults to [name] in lower case with spaces as `-`. Velocity only. */
    abstract val id: Property<String>

    /** Plugin version. Defaults to the project version. */
    abstract val version: Property<String>

    /** Main class. Required. */
    abstract val main: Property<String>

    /** Description. Defaults to the project description. */
    abstract val description: Property<String>

    /** Website, `url` in `velocity-plugin.json`. */
    abstract val website: Property<String>

    /** Authors; joined into `author` in `bungee.yml`. */
    abstract val authors: ListProperty<String>

    /** Contributors. Bukkit and Paper only. */
    abstract val contributors: ListProperty<String>

    /** Prefix of the plugin's log lines. Bukkit and Paper only. */
    abstract val prefix: Property<String>

    /** Lowest server API version the plugin works with, e.g. `"1.20"`. Required for `paper-plugin.yml`. */
    abstract val apiVersion: Property<String>

    /** Whether the plugin is enabled before or after the worlds load. Bukkit and Paper only. */
    abstract val load: Property<LoadOrder>

    /** Default of permissions that do not set one. Bukkit and Paper only. */
    abstract val defaultPermission: Property<PermissionDefault>

    /** Other plugin names this plugin also answers to. Bukkit and Paper only. */
    abstract val provides: ListProperty<String>

    /** Whether the plugin runs on Folia. Defaults to `true` with `platform = ServerPlatform.FOLIA`. */
    abstract val foliaSupported: Property<Boolean>

    /** Plugins that must be present and enabled before this one. */
    abstract val depend: ListProperty<String>

    /** Plugins enabled before this one when they are present. */
    abstract val softDepend: ListProperty<String>

    /** Plugins enabled after this one when they are present. Bukkit and Paper only. */
    abstract val loadBefore: ListProperty<String>

    /** Whether `paper-plugin.yml` is written instead of `plugin.yml`. Defaults to `false`. */
    abstract val paperPlugin: Property<Boolean>

    /** `PluginBootstrap` class. `paper-plugin.yml` only. */
    abstract val bootstrapper: Property<String>

    /** `PluginLoader` class. `paper-plugin.yml` only. */
    abstract val loader: Property<String>

    /** Whether other plugins can see this plugin's classes without depending on it. `paper-plugin.yml` only. */
    abstract val hasOpenClassloader: Property<Boolean>

    /** Keys written as they are after the generated ones, for fields this block has no property for. */
    abstract val extra: MapProperty<String, Any>

    /** Commands. Bukkit `plugin.yml` only; Paper plugins register commands in code. */
    val commands: NamedDomainObjectContainer<CommandSpec> = objects.domainObjectContainer(CommandSpec::class.java)

    /** Permissions. Bukkit and Paper only. */
    val permissions: NamedDomainObjectContainer<PermissionSpec> = objects.domainObjectContainer(PermissionSpec::class.java)

    /** Adds authors. */
    fun authors(vararg names: String) {
        authors.addAll(*names)
    }

    /** Adds contributors. */
    fun contributors(vararg names: String) {
        contributors.addAll(*names)
    }

    /** Adds plugins that must be present. */
    fun depend(vararg plugins: String) {
        depend.addAll(*plugins)
    }

    /** Adds plugins loaded first when present. */
    fun softDepend(vararg plugins: String) {
        softDepend.addAll(*plugins)
    }

    /** Adds plugins loaded after this one. */
    fun loadBefore(vararg plugins: String) {
        loadBefore.addAll(*plugins)
    }

    /** Adds names this plugin provides. */
    fun provides(vararg names: String) {
        provides.addAll(*names)
    }

    /** Writes `paper-plugin.yml` instead of `plugin.yml`. */
    fun paperPlugin() {
        paperPlugin.set(true)
    }

    /** Sets [load] by name, e.g. `load 'startup'` in the Groovy DSL. */
    fun load(name: String) {
        load.set(LoadOrder.valueOf(name.trim().uppercase()))
    }

    /**
     * Writes [key] with a value this block has no property for: a string, a boolean, a number or a list of strings,
     * e.g. `extra("paper-skip-libraries", true)`.
     */
    fun extra(key: String, value: Any) {
        val valid = value is String || value is Boolean || value is Int || value is Long ||
            value is List<*> && value.all { it is String }
        require(valid) { "extra(\"$key\", ...) takes a String, Boolean, Int, Long or a list of strings, not ${value::class.java.name}" }
        extra.put(key, if (value is List<*>) value.toList() else value)
    }

    /** Configures the commands. */
    fun commands(configure: Action<NamedDomainObjectContainer<CommandSpec>>) {
        configure.execute(commands)
    }

    /** Configures the permissions. */
    fun permissions(configure: Action<NamedDomainObjectContainer<PermissionSpec>>) {
        configure.execute(permissions)
    }

    internal fun snapshot(platform: ServerPlatform?): PluginDescriptor = PluginDescriptor(
        platform = platform,
        paperPlugin = paperPlugin.get(),
        name = name.orNull,
        id = id.orNull,
        version = version.orNull,
        main = main.orNull,
        description = description.orNull,
        website = website.orNull,
        authors = authors.get().toList(),
        contributors = contributors.get().toList(),
        prefix = prefix.orNull,
        apiVersion = apiVersion.orNull,
        load = load.orNull,
        defaultPermission = defaultPermission.orNull,
        provides = provides.get().toList(),
        foliaSupported = foliaSupported.getOrElse(false),
        depend = depend.get().toList(),
        softDepend = softDepend.get().toList(),
        loadBefore = loadBefore.get().toList(),
        bootstrapper = bootstrapper.orNull,
        loader = loader.orNull,
        hasOpenClassloader = hasOpenClassloader.orNull,
        commands = commands.map {
            PluginDescriptor.Command(it.name, it.description.orNull, it.aliases.get().toList(), it.permission.orNull, it.permissionMessage.orNull, it.usage.orNull)
        },
        permissions = permissions.map {
            PluginDescriptor.Permission(it.name, it.description.orNull, it.default.orNull, LinkedHashMap(it.children.get()))
        },
        extra = LinkedHashMap(extra.get())
    )
}

/**
 * A command of `plugin.yml`.
 */
abstract class CommandSpec @Inject constructor(private val name: String) : Named {
    override fun getName(): String = name

    /** Description shown in `/help`. */
    abstract val description: Property<String>

    /** Other names of the command. */
    abstract val aliases: ListProperty<String>

    /** Permission needed to run the command. */
    abstract val permission: Property<String>

    /** Message sent to players without [permission]. */
    abstract val permissionMessage: Property<String>

    /** Usage shown when the executor returns `false`; `<command>` is replaced with the label. */
    abstract val usage: Property<String>

    /** Adds aliases. */
    fun aliases(vararg names: String) {
        aliases.addAll(*names)
    }
}

/**
 * A permission of `plugin.yml` or `paper-plugin.yml`.
 */
abstract class PermissionSpec @Inject constructor(private val name: String) : Named {
    override fun getName(): String = name

    /** Description. */
    abstract val description: Property<String>

    /** Who has the permission by default. The server's default is [OP]. */
    abstract val default: Property<PermissionDefault>

    /** Child permissions, granted (`true`) or revoked (`false`) with this one. */
    abstract val children: MapProperty<String, Boolean>

    /** Everyone. */
    val TRUE: PermissionDefault get() = PermissionDefault.TRUE

    /** No one. */
    val FALSE: PermissionDefault get() = PermissionDefault.FALSE

    /** Operators. */
    val OP: PermissionDefault get() = PermissionDefault.OP

    /** Everyone except operators. */
    val NOT_OP: PermissionDefault get() = PermissionDefault.NOT_OP

    /** Adds child permissions granted with this one. */
    fun children(vararg names: String) {
        names.forEach { children.put(it, true) }
    }
}
