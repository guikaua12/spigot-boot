/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.approximations.spigotboot.placeholder.registry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.di.manager.DependencyManager;
import me.approximations.spigotboot.core.reflection.DiscoveryService;
import me.approximations.spigotboot.core.utils.ReflectionUtils;
import me.approximations.spigotboot.placeholder.annotations.Placeholder;
import me.approximations.spigotboot.placeholder.metadata.PlaceholderMetadata;
import me.approximations.spigotboot.placeholder.metadata.parser.PlaceholderParameterParser;
import me.approximations.spigotboot.placeholder.papi.PAPIExpansion;
import me.approximations.spigotboot.placeholder.registry.discovery.PlaceholderDiscoveryService;
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
