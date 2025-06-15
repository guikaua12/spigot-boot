package me.approximations.apxPlugin.core.context.configuration.processor;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.context.configuration.annotations.Bean;
import me.approximations.apxPlugin.core.context.configuration.annotations.Configuration;
import me.approximations.apxPlugin.core.di.annotations.Component;
import me.approximations.apxPlugin.core.di.manager.DependencyManager;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ConfigurationProcessor {
    private final DependencyManager dependencyManager;

    public void processFromPackage(Class<?>... clazz) {
        for (Class<?> baseClass : clazz) {
            for (Class<?> configClass : ReflectionUtils.getClassesAnnotatedWith(baseClass, Configuration.class)) {
                processClass(configClass);
            }
        }
    }

    public void processClass(Class<?> clazz) {
        try {
            dependencyManager.registerDependency(clazz);
            Object configObject = dependencyManager.resolveDependency(clazz);

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Bean.class)) continue;

                if (!Object.class.isAssignableFrom(method.getReturnType())) {
                    throw new RuntimeException("Method '" + method.getName() + "' in class '" + clazz.getName() + "' must return an Object type.");
                }

                try {
                    Object[] parameterDependencies = Arrays.stream(method.getParameterTypes())
                            .map(dependencyManager::resolveDependency)
                            .toArray();

                    Object beanInstance = method.invoke(configObject, parameterDependencies);
                    dependencyManager.registerDependency(method.getReturnType(), beanInstance);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to process configuration class: '" + clazz.getName() + "'", t);
        }
    }
}
