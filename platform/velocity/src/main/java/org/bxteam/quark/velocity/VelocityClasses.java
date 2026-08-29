package org.bxteam.quark.velocity;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

/**
 * Class presence checks that are safe to run when the Velocity API is missing.
 */
final class VelocityClasses {
    private static final String PLUGIN_ANNOTATION = "com.velocitypowered.api.plugin.Plugin";

    private VelocityClasses() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    @SuppressWarnings("unchecked")
    static boolean isVelocityPlugin(@NotNull Object plugin) {
        try {
            Class<? extends Annotation> annotation = (Class<? extends Annotation>)
                    Class.forName(PLUGIN_ANNOTATION, false, plugin.getClass().getClassLoader());
            return plugin.getClass().isAnnotationPresent(annotation);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
