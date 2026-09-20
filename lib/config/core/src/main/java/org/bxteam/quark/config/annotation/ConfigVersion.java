package org.bxteam.quark.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The version of a configuration class, stored in the file under {@link #key()}.
 *
 * <p>When a file with an older version is loaded, the {@link org.bxteam.quark.config.Migration}s registered in
 * {@link org.bxteam.quark.config.ConfigOptions#migrations(org.bxteam.quark.config.Migration...)} bring it up to
 * date step by step. A file without the key has version 1, so the first version of a class needs no annotation.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigVersion {
    /**
     * @return the current version, at least 1
     */
    int value();

    /**
     * @return the key the version is stored under
     */
    String key() default "config-version";
}
