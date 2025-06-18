package me.approximations.spigotboot.annotationprocessor.annotations;

import me.approximations.spigotboot.annotationprocessor.plugin.PluginLoadOrder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Plugin {
    String name();

    String version();

    String description() default "";

    PluginLoadOrder load() default PluginLoadOrder.POSTWORLD;

    String author() default "";

    String[] authors() default {};

    String website() default "";

    String[] depend() default {};

    String[] softdepend() default {};

    String[] loadbefore() default {};

    String prefix() default "";

    String[] libraries() default {};

    String apiVersion() default "";
}

