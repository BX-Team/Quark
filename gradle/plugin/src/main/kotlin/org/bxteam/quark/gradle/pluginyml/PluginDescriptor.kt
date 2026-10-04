package org.bxteam.quark.gradle.pluginyml

import org.bxteam.quark.gradle.ServerPlatform
import org.gradle.api.GradleException
import java.io.Serializable

/**
 * When a Bukkit or Paper plugin is enabled: before the worlds are loaded or after.
 */
enum class LoadOrder {
    STARTUP,
    POSTWORLD
}

/**
 * Who gets a permission by default.
 */
enum class PermissionDefault(internal val value: Any) {
    TRUE(true),
    FALSE(false),
    OP("op"),
    NOT_OP("!op")
}

/**
 * The descriptor file a platform reads.
 */
internal enum class DescriptorFormat(val fileName: String) {
    BUKKIT("plugin.yml"),
    PAPER("paper-plugin.yml"),
    BUNGEE("bungee.yml"),
    VELOCITY("velocity-plugin.json")
}

/**
 * What `quark { pluginYml { } }` says, resolved at configuration time; the task input of `generatePluginYml`.
 */
internal data class PluginDescriptor(
    val platform: ServerPlatform?,
    val paperPlugin: Boolean,
    val name: String?,
    val id: String?,
    val version: String?,
    val main: String?,
    val description: String?,
    val website: String?,
    val authors: List<String>,
    val contributors: List<String>,
    val prefix: String?,
    val apiVersion: String?,
    val load: LoadOrder?,
    val defaultPermission: PermissionDefault?,
    val provides: List<String>,
    val foliaSupported: Boolean,
    val depend: List<String>,
    val softDepend: List<String>,
    val loadBefore: List<String>,
    val bootstrapper: String?,
    val loader: String?,
    val hasOpenClassloader: Boolean?,
    val commands: List<Command>,
    val permissions: List<Permission>,
    val extra: Map<String, Any>
) : Serializable {
    data class Command(
        val name: String,
        val description: String?,
        val aliases: List<String>,
        val permission: String?,
        val permissionMessage: String?,
        val usage: String?
    ) : Serializable

    data class Permission(
        val name: String,
        val description: String?,
        val default: PermissionDefault?,
        val children: Map<String, Boolean>
    ) : Serializable

    /**
     * The file for [platform], after checking that every field set is valid there.
     */
    fun format(): DescriptorFormat {
        val format = when (platform) {
            null -> fail("needs the platform to pick the file: set `quark { platform = ServerPlatform.PAPER }` (or BUKKIT, FOLIA, BUNGEE, VELOCITY)")
            ServerPlatform.PAPER, ServerPlatform.FOLIA -> if (paperPlugin) DescriptorFormat.PAPER else DescriptorFormat.BUKKIT
            ServerPlatform.BUKKIT -> DescriptorFormat.BUKKIT
            ServerPlatform.BUNGEE -> DescriptorFormat.BUNGEE
            ServerPlatform.VELOCITY -> DescriptorFormat.VELOCITY
        }
        if (paperPlugin && format != DescriptorFormat.PAPER) {
            fail("paperPlugin() needs `quark { platform = ServerPlatform.PAPER }` or FOLIA, the platform is $platform")
        }
        validate(format)
        return format
    }

    private fun validate(format: DescriptorFormat) {
        val name = name?.takeIf { it.isNotBlank() } ?: fail("set the plugin name: `pluginYml { name = \"MyPlugin\" }`")
        if (version.isNullOrBlank() || version == "unspecified") {
            fail("set the plugin version: `version = \"1.0.0\"` in the project or `pluginYml { version = \"1.0.0\" }`")
        }
        val main = main?.takeIf { it.isNotBlank() } ?: fail("set the main class: `pluginYml { main = \"com.example.MyPlugin\" }`")

        if (format == DescriptorFormat.VELOCITY) {
            if (!VELOCITY_ID.matches(velocityId())) {
                fail("Velocity plugin id '${velocityId()}' must be lowercase letters, digits, '-' and '_', starting with a letter; set it with `pluginYml { id = \"myplugin\" }`")
            }
        } else if (!VALID_NAME.matches(name)) {
            fail("plugin name '$name' may contain only letters, digits, spaces, '_', '.' and '-'")
        }

        if (format == DescriptorFormat.BUKKIT || format == DescriptorFormat.PAPER) {
            listOfNotNull("main" to main, bootstrapper?.let { "bootstrapper" to it }, loader?.let { "loader" to it }).forEach { (field, type) ->
                RESERVED_PACKAGES.firstOrNull { type.startsWith(it) }?.let { fail("$field '$type' may not be in the server's package $it, move it to your own package") }
            }
            validateApiVersion(format)
        }

        unsupported(format, "commands", commands.isNotEmpty(), DescriptorFormat.BUKKIT)?.let {
            if (format == DescriptorFormat.PAPER) {
                fail("paper-plugin.yml has no commands, register them in code with LifecycleEvents.COMMANDS or drop paperPlugin()")
            }
            fail(it)
        }
        unsupported(format, "permissions", permissions.isNotEmpty(), DescriptorFormat.BUKKIT, DescriptorFormat.PAPER)?.let(::fail)
        unsupported(format, "defaultPermission", defaultPermission != null, DescriptorFormat.BUKKIT, DescriptorFormat.PAPER)?.let(::fail)
        unsupported(format, "loadBefore", loadBefore.isNotEmpty(), DescriptorFormat.BUKKIT, DescriptorFormat.PAPER)?.let(::fail)
        unsupported(format, "bootstrapper", bootstrapper != null, DescriptorFormat.PAPER)?.let(::fail)
        unsupported(format, "loader", loader != null, DescriptorFormat.PAPER)?.let(::fail)
        unsupported(format, "hasOpenClassloader", hasOpenClassloader != null, DescriptorFormat.PAPER)?.let(::fail)
        unsupported(format, "id", id != null, DescriptorFormat.VELOCITY)?.let(::fail)

        for (command in commands) {
            (listOf(command.name) + command.aliases).firstOrNull { it.isBlank() || ':' in it || ' ' in it }?.let {
                fail("command or alias '$it' of '${command.name}' may not be blank or contain ':' or spaces")
            }
        }
    }

    private fun validateApiVersion(format: DescriptorFormat) {
        val apiVersion = apiVersion ?: if (format == DescriptorFormat.PAPER) {
            fail("paper-plugin.yml needs the API version: `pluginYml { apiVersion = \"1.21\" }`")
        } else {
            return
        }
        val parts = API_VERSION.matchEntire(apiVersion)?.groupValues?.drop(1)?.filter { it.isNotEmpty() }?.map { it.toInt() }
            ?: fail("apiVersion '$apiVersion' is not a Minecraft version like \"1.20\", \"1.20.6\" or \"26.1\"")
        val minimum = if (format == DescriptorFormat.PAPER) 19 else 13
        if (parts[0] == 1 && parts[1] < minimum) {
            fail("apiVersion '$apiVersion' is older than the oldest one ${format.fileName} accepts, use at least \"1.$minimum\"")
        }
        if (parts[0] == 1 && parts.size == 3 && (parts[1] < 20 || parts[1] == 20 && parts[2] < 5)) {
            fail("apiVersion '$apiVersion' has a patch version, which the server accepts only from 1.20.5; use \"1.${parts[1]}\"")
        }
    }

    /**
     * The file content for [format], with the [extra] keys after the generated ones.
     */
    fun render(format: DescriptorFormat): String {
        val generated = when (format) {
            DescriptorFormat.BUKKIT -> bukkit()
            DescriptorFormat.PAPER -> paper()
            DescriptorFormat.BUNGEE -> bungee()
            DescriptorFormat.VELOCITY -> velocity()
        }
        extra.keys.firstOrNull { it in generated }?.let {
            fail("extra(\"$it\", ...) sets a key pluginYml already writes, use the matching field instead")
        }
        return if (format == DescriptorFormat.VELOCITY) Json.write(generated + extra) else Yaml.write(generated + extra)
    }

    private fun bukkit() = entries(
        "name" to name,
        "version" to version,
        "main" to main,
        "description" to description,
        "api-version" to apiVersion,
        "load" to load?.name,
        "authors" to authors,
        "contributors" to contributors,
        "website" to website,
        "prefix" to prefix,
        "depend" to depend,
        "softdepend" to softDepend,
        "loadbefore" to loadBefore,
        "provides" to provides,
        "default-permission" to defaultPermission?.value,
        "folia-supported" to foliaSupported.takeIf { it },
        "commands" to commands.associate { command ->
            command.name to entries(
                "description" to command.description,
                "aliases" to command.aliases,
                "permission" to command.permission,
                "permission-message" to command.permissionMessage,
                "usage" to command.usage
            )
        },
        "permissions" to permissionEntries()
    )

    // Paper reads `defaultPerm` in camel case, unlike the kebab-case keys around it
    private fun paper() = entries(
        "name" to name,
        "version" to version,
        "main" to main,
        "bootstrapper" to bootstrapper,
        "loader" to loader,
        "description" to description,
        "api-version" to apiVersion,
        "load" to load?.name,
        "authors" to authors,
        "contributors" to contributors,
        "website" to website,
        "prefix" to prefix,
        "provides" to provides,
        "defaultPerm" to defaultPermission?.value,
        "has-open-classloader" to hasOpenClassloader,
        "folia-supported" to foliaSupported.takeIf { it },
        "dependencies" to entries(
            "server" to depend.associateWith { mapOf("load" to "BEFORE", "required" to true) } +
                softDepend.associateWith { mapOf("load" to "BEFORE", "required" to false) } +
                loadBefore.associateWith { mapOf("load" to "AFTER", "required" to false) }
        ),
        "permissions" to permissionEntries()
    )

    private fun bungee() = entries(
        "name" to name,
        "main" to main,
        "version" to version,
        "author" to authors.joinToString(", ").ifEmpty { null },
        "depends" to depend,
        "softDepends" to softDepend,
        "description" to description
    )

    private fun velocity() = entries(
        "id" to velocityId(),
        "name" to name,
        "version" to version,
        "description" to description,
        "url" to website,
        "authors" to authors,
        "dependencies" to depend.map { mapOf("id" to it, "optional" to false) } + softDepend.map { mapOf("id" to it, "optional" to true) },
        "main" to main
    )

    private fun permissionEntries() = permissions.associate { permission ->
        permission.name to entries(
            "description" to permission.description,
            "default" to permission.default?.value,
            "children" to permission.children
        )
    }

    /**
     * The set, non-empty entries; a command or permission without fields is still written, as an empty map.
     */
    private fun entries(vararg pairs: Pair<String, Any?>): Map<String, Any> {
        val map = linkedMapOf<String, Any>()
        for ((key, value) in pairs) {
            when (value) {
                null -> {}
                is Collection<*> -> if (value.isNotEmpty()) map[key] = value
                is Map<*, *> -> if (value.isNotEmpty()) map[key] = value
                else -> map[key] = value
            }
        }
        return map
    }

    private fun velocityId(): String = id ?: name.orEmpty().lowercase().replace(' ', '-')

    private fun unsupported(format: DescriptorFormat, field: String, set: Boolean, vararg supported: DescriptorFormat): String? =
        if (set && format !in supported) "$field is not supported in ${format.fileName}, remove it from pluginYml { }" else null

    private companion object {
        val VALID_NAME = Regex("^[A-Za-z0-9 _.-]+$")
        val VELOCITY_ID = Regex("^[a-z][a-z0-9-_]{0,63}$")
        val API_VERSION = Regex("^(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{1,2}))?$")
        val RESERVED_PACKAGES = listOf("net.minecraft.", "org.bukkit.", "org.spigotmc.", "io.papermc.", "com.destroystokoyo.paper.")

        fun fail(message: String): Nothing = throw GradleException("quark { pluginYml { } }: $message")
    }
}
