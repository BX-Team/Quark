package org.bxteam.quark.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `org.bxteam.runserver` 2.x: run-server-plugin moved into the Quark Gradle plugin.
 *
 * Applies `org.bxteam.quark` and prints one warning with the migration guide. The old
 * `tasks.runServer { ... }` DSL is gone, dev servers are configured in `quark { devServer { ... } }`.
 */
class RunServerShimPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.warn(
            "The 'org.bxteam.runserver' plugin is deprecated and replaced by 'org.bxteam.quark': " +
                "configure dev servers in quark { devServer { ... } }. Migration guide: $MIGRATION_GUIDE"
        )
        project.pluginManager.apply(QuarkPlugin::class.java)
    }

    internal companion object {
        const val MIGRATION_GUIDE = "https://bxteam.org/docs/quark/migration/run-server-plugin"
    }
}
