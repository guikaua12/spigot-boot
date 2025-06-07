package me.approximations.apxPlugin.core.context.component.proxy.methodHandler.processor;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.annotations.RegisterMethodHandler;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import me.approximations.apxPlugin.core.di.annotations.Component;
import me.approximations.apxPlugin.core.di.manager.DependencyManager;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MethodHandlerProcessor {
    private final DependencyManager dependencyManager;

    public List<RegisteredMethodHandler> processFromPackage(Class<?>... bases) {
        return Arrays.stream(bases)
                .map(base -> ReflectionUtils.getClassesAnnotatedWith(base, RegisterMethodHandler.class))
                .flatMap(Set::stream)
                .flatMap(clazz -> processClass(clazz).stream())
                .collect(Collectors.toList());
    }

    private List<RegisteredMethodHandler> processClass(Class<?> clazz) {
        try {
            dependencyManager.registerDependency(clazz);
            Object handler = dependencyManager.resolveDependency(clazz);

            return Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(MethodHandler.class))
                    .filter(method -> method.getParameterCount() == 1 && method.getParameterTypes()[0] == MethodHandlerContext.class)
                    .map(method -> {
                        MethodHandler annotation = method.getAnnotation(MethodHandler.class);
                        return new RegisteredMethodHandler(
                                context -> method.invoke(handler, context),
                                annotation.targetClass(),
                                annotation.classAnnotatedWith(),
                                annotation.methodAnnotatedWith()
                        );
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to process handler: " + clazz.getName(), e);
        }
    }
}
