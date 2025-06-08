package me.approximations.apxPlugin.placeholder.registry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.di.manager.DependencyManager;
import me.approximations.apxPlugin.core.reflection.DiscoveryService;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;
import me.approximations.apxPlugin.placeholder.annotations.Placeholder;
import me.approximations.apxPlugin.placeholder.metadata.PlaceholderMetadata;
import me.approximations.apxPlugin.placeholder.metadata.parser.PlaceholderParameterParser;
import me.approximations.apxPlugin.placeholder.papi.PAPIExpansion;
import me.approximations.apxPlugin.placeholder.registry.discovery.PlaceholderDiscoveryService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public class PlaceholderRegistry {
    private final Map<String, PlaceholderMetadata> placeholders = new HashMap<>();
    private final PAPIExpansion papiExpansion;
    private final Plugin plugin;
    private final DependencyManager dependencyManager;

    public void initialize() {
        final DiscoveryService<Class<?>> discoveryService = new PlaceholderDiscoveryService(plugin);

        for (Class<?> clazz : discoveryService.discoverAll()) {
            dependencyManager.registerDependency(clazz);
            Object handlerObject = dependencyManager.resolveDependency(clazz);

            final Set<Method> handlerMethods = ReflectionUtils.getMethodsAnnotatedWith(Placeholder.class, clazz);
            for (Method method : handlerMethods) {
                final Placeholder placeholderAnnotation = method.getAnnotation(Placeholder.class);
                if (placeholderAnnotation == null) {
                    continue;
                }

                validateHandlerMethod(method);

                final PlaceholderMetadata metadata = new PlaceholderMetadata(
                        handlerObject,
                        method,
                        placeholderAnnotation.value(),
                        placeholderAnnotation.description(),
                        placeholderAnnotation.placeholderApi()
                );

                placeholders.put(metadata.getPlaceholder(), metadata);
            }
        }

        if (!papiExpansion.register()) {
            plugin.getLogger().warning("Failed to register placeholder expansion for plugin: " + plugin.getName());
        }
    }

    public void unregister() {
        if (papiExpansion.isRegistered()) {
            papiExpansion.unregister();
        }
        placeholders.clear();
    }

    private void validateHandlerMethod(Method method) {
        try {
            if (method.getParameterCount() != 2) {
                throw new IllegalStateException("Placeholder method must have two parameters, found: (" + method.getParameterCount() + ")");
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!parameterTypes[0].equals(Player.class) || !parameterTypes[1].equals(String.class)) {
                throw new IllegalStateException(
                        String.format(
                                "Placeholder method must have parameters (%s, %s), found: (%s)",
                                Player.class.getName(), String.class.getName(),
                                Arrays.stream(parameterTypes).map(Class::getName).collect(Collectors.joining(", "))
                        )
                );
            }

            Class<?> returnType = method.getReturnType();
            if (!returnType.equals(String.class)) {
                throw new IllegalStateException(
                        String.format("Placeholder method must return %s, found: %s", String.class.getName(), returnType.getName())
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid placeholder method: " + method.getName(), e);
        }
    }

    public @Nullable PlaceholderMetadata findPlaceholderMetadata(String params) {
        return placeholders.values().stream()
                .filter(metadata -> metadata.getPlaceholder().equals(params) ||
                        PlaceholderParameterParser.isValidPlaceholderPattern(metadata.getPlaceholder(), params)
                ).findFirst().orElse(null);
    }
}
