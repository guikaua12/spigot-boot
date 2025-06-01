package me.approximations.apxPlugin.core.context.component.proxy.methodHandler.processor;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.MethodHandler;
import me.approximations.apxPlugin.core.di.annotations.Component;
import me.approximations.apxPlugin.core.di.manager.DependencyManager;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MethodHandlerProcessor {
    private final DependencyManager dependencyManager;

    @SuppressWarnings("unchecked")
    public Map<Class<?>, List<MethodHandlerProcessResult>> processFromPackage(Class<?>... bases) {
        return Arrays.stream(bases)
                .map(base -> ReflectionUtils.getSubClassesOf(base, MethodHandler.class))
                .flatMap(Set::stream)
                .map(this::processClass)
                .collect(Collectors.groupingBy(MethodHandlerProcessResult::getTargetClass));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public MethodHandlerProcessResult processClass(Class<? extends MethodHandler> clazz) {
        try {
            dependencyManager.registerDependency(clazz);
            MethodHandler<?> handler = dependencyManager.resolveDependency(clazz);
            Class<?> target = Arrays.stream(clazz.getGenericInterfaces())
                    .filter(i -> i instanceof ParameterizedType)
                    .map(i -> (ParameterizedType) i)
                    .filter(t -> MethodHandler.class.equals(t.getRawType()))
                    .map(t -> (Class<?>) ((ParameterizedType) t.getActualTypeArguments()[0]).getRawType())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(
                            "Invalid handler type: " + clazz.getName() + ". Ensure it implements MethodHandler<TargetClass>."
                    ));
            return new MethodHandlerProcessResult(target, handler);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process handler: " + clazz.getName(), e);
        }
    }
}
