package me.approximations.spigotboot.core.context.component.proxy.methodHandler.annotations;

import me.approximations.spigotboot.core.di.annotations.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterMethodHandler {
}
