package tech.guilhermekaua.spigotboot.core.utils;

import tech.guilhermekaua.spigotboot.core.context.annotations.OnReload;
import tech.guilhermekaua.spigotboot.core.context.annotations.Primary;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class BeanUtils {
    public static String getQualifier(AnnotatedElement element) {
        return element.isAnnotationPresent(Qualifier.class) ?
                element.getAnnotation(Qualifier.class).value() :
                null;
    }

    public static boolean getIsPrimary(AnnotatedElement element) {
        return element.isAnnotationPresent(Primary.class);
    }

    public static DependencyReloadCallback createDependencyReloadCallback(Class<?> clazz) {
        return (instance, dependencyManager) -> {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(OnReload.class)) {
                    continue;
                }

                try {
                    Object[] dependencies = Arrays.stream(method.getParameterTypes())
                            .map(param -> dependencyManager.resolveDependency(param, getQualifier(method)))
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
