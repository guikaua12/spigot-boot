package tech.guilhermekaua.spigotboot.commands.metadata;

import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class NestedCommandHandlerInstantiator {
    Object instantiate(Object enclosingInstance, Class<?> nestedType, DependencyManager dependencyManager) {
        Objects.requireNonNull(enclosingInstance, "enclosingInstance cannot be null.");
        Objects.requireNonNull(nestedType, "nestedType cannot be null.");

        try {
            Constructor<?> constructor = dependencyManager != null
                    ? dependencyManager.findInjectConstructor(nestedType)
                    : findInjectConstructor(nestedType);

            if (constructor == null) {
                throw new IllegalStateException("Nested command class must declare an instantiable constructor: " + nestedType.getName());
            }

            Object[] arguments = resolveConstructorArguments(enclosingInstance, nestedType, constructor, dependencyManager);
            constructor.setAccessible(true);

            Object rawInstance = constructor.newInstance(arguments);
            if (dependencyManager == null) {
                return rawInstance;
            }

            BeanDefinition definition = new BeanDefinition(
                    nestedType,
                    nestedType,
                    nestedType.getName() + "#nestedCommand",
                    false,
                    null,
                    null
            );
            return dependencyManager.initializeBean(definition, rawInstance);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate nested command class: " + nestedType.getName(), e);
        }
    }

    private Object[] resolveConstructorArguments(Object enclosingInstance,
                                                 Class<?> nestedType,
                                                 Constructor<?> constructor,
                                                 DependencyManager dependencyManager) {
        List<Object> arguments = new ArrayList<>();
        Parameter[] parameters = constructor.getParameters();
        int startIndex = 0;

        if (nestedType.isMemberClass() && !Modifier.isStatic(nestedType.getModifiers())) {
            if (parameters.length == 0) {
                throw new IllegalStateException("Non-static nested command class is missing the enclosing instance parameter: " + nestedType.getName());
            }

            Class<?> outerType = parameters[0].getType();
            if (!outerType.isInstance(enclosingInstance)) {
                throw new IllegalStateException(
                        "Non-static nested command class requires enclosing type " + outerType.getName() + " but got " +
                                enclosingInstance.getClass().getName()
                );
            }

            arguments.add(enclosingInstance);
            startIndex = 1;
        }

        for (int index = startIndex; index < parameters.length; index++) {
            if (dependencyManager == null) {
                throw new IllegalStateException(
                        "Nested command class requires DependencyManager-backed constructor injection: " + nestedType.getName()
                );
            }
            arguments.add(dependencyManager.resolveDependency(InjectionPoint.fromParameter(parameters[index])));
        }

        return arguments.toArray(new Object[0]);
    }

    private Constructor<?> findInjectConstructor(Class<?> type) {
        if (type.isInterface()) {
            return null;
        }

        Constructor<?>[] constructors = type.getDeclaredConstructors();
        Constructor<?> injectConstructor = null;
        for (Constructor<?> constructor : constructors) {
            if (!constructor.isAnnotationPresent(Inject.class)) {
                continue;
            }
            if (injectConstructor != null) {
                throw new IllegalStateException("Multiple constructors annotated with @Inject found for " + type.getName());
            }
            injectConstructor = constructor;
        }

        if (injectConstructor != null) {
            return injectConstructor;
        }

        if (constructors.length != 1) {
            throw new IllegalStateException("Nested command class requires exactly one constructor or a single @Inject constructor: " + type.getName());
        }
        return constructors[0];
    }
}
