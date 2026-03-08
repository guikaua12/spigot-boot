package tech.guilhermekaua.spigotboot.commands.annotations;

import tech.guilhermekaua.spigotboot.commands.CommandAnnotationInterceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface CommandInterceptedBy {
    Class<? extends CommandAnnotationInterceptor<?>>[] value();
}
