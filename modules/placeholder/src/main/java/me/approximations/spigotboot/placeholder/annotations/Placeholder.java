package me.approximations.spigotboot.placeholder.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Placeholder {
    String value();

    char delimiter() default '%';

    String description() default "";

    boolean placeholderApi() default true;
}
