package tech.guilhermekaua.spigotboot.core.utils;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnReload;
import tech.guilhermekaua.spigotboot.core.context.annotations.Primary;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

public final class BeanUtils {
    public static String getQualifier(@NotNull AnnotatedElement element) {
        Objects.requireNonNull(element);

        return element.isAnnotationPresent(Qualifier.class) ?
                element.getAnnotation(Qualifier.class).value() :
                null;
    }

    public static boolean getIsPrimary(@NotNull AnnotatedElement element) {
        Objects.requireNonNull(element);

        return element.isAnnotationPresent(Primary.class);
    }

    public static DependencyReloadCallback createDependencyReloadCallback(@NotNull Class<?> clazz) {
        Objects.requireNonNull(clazz);

        return (instance, dependencyManager) -> {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(OnReload.class)) {
                    continue;
                }

                try {
                    Object[] dependencies = Arrays.stream(method.getParameters())
                            .map(param -> dependencyManager.resolveDependency(param.getType(), getQualifier(param)))
                            .toArray(Object[]::new);

                    method.setAccessible(true);
                    method.invoke(instance, dependencies);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
