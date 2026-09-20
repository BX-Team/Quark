package org.bxteam.quark.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Comment lines written above the key of a field. An empty line becomes a blank line.
 *
 * <pre>{@code
 * @Comment({"Message prefix", "Supports legacy color codes"})
 * public String prefix = "&7[MyPlugin] ";
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Comment {
    /**
     * @return the comment lines
     */
    String[] value();
}
