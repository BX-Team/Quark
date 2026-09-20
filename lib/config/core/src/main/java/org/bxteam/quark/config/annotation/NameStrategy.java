package org.bxteam.quark.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How the field names of a class become keys. Nested classes without their own annotation use the strategy
 * of the class they are part of; without any annotation keys are the field names.
 *
 * <pre>{@code
 * @NameStrategy(NameStyle.HYPHEN_CASE)
 * public class MyConfig extends QuarkConfig {
 *     public int maxPlayers = 20; // max-players: 20
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NameStrategy {
    /**
     * @return the naming style
     */
    NameStyle value();
}
