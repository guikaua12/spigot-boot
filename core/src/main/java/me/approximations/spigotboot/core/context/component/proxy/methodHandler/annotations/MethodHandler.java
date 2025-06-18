package me.approximations.spigotboot.core.context.component.proxy.methodHandler.annotations;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MethodHandler {
    Class<?> targetClass() default void.class;

    Class<? extends Annotation> classAnnotatedWith() default Annotation.class;

    Class<? extends Annotation> methodAnnotatedWith() default Annotation.class;
}
