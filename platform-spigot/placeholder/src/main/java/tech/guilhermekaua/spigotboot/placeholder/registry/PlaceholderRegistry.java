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
package tech.guilhermekaua.spigotboot.placeholder.registry;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnClass;
import tech.guilhermekaua.spigotboot.core.context.condition.LogLevel;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.reflection.DiscoveryService;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.placeholder.annotations.Placeholder;
import tech.guilhermekaua.spigotboot.placeholder.metadata.PlaceholderMetadata;
import tech.guilhermekaua.spigotboot.placeholder.papi.PAPIExpansion;
import tech.guilhermekaua.spigotboot.placeholder.registry.discovery.PlaceholderDiscoveryService;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Discovers placeholder handlers and wires them into the PlaceholderAPI expansion.
 * <p>
 * The registry owns the discovery and registration flow: it scans for placeholder handler classes,
 * resolves them as beans, builds their {@link PlaceholderMetadata}, publishes that metadata into the shared
 * {@link PlaceholderStore}, and manages the {@link PAPIExpansion} lifecycle. It deliberately depends on the
 * {@link PlaceholderStore} rather than holding the lookup table itself, so the expansion can read placeholders
 * from the same store without creating a registry/expansion dependency cycle.
 */
@Component
@ConditionalOnClass(value = "me.clip.placeholderapi.expansion.PlaceholderExpansion", message = "PlaceholderAPI not found, skipping PlaceholderRegistry bean.", logLevel = LogLevel.DEBUG)
@RequiredArgsConstructor
public class PlaceholderRegistry {
    private final PlaceholderStore placeholderStore;
    private final PAPIExpansion papiExpansion;
    private final Plugin plugin;
    private final DependencyManager dependencyManager;

    /**
     * Discovers placeholder handlers, publishes their metadata into the {@link PlaceholderStore}, and registers
     * the PlaceholderAPI expansion.
     */
    public void initialize() {
        final DiscoveryService<Class<?>> discoveryService = new PlaceholderDiscoveryService(plugin);

        for (Class<?> clazz : discoveryService.discoverAll()) {
            // the handler is already a registered bean: @RegisterPlaceholder is meta-annotated @Component,
            // so the component scan registers it during the SCAN phase (before modules initialize). resolve
            // that existing bean rather than registering it again, which would collide on the qualifier and
            // abort plugin enable.
            Object handlerObject = dependencyManager.resolveDependency(clazz, BeanUtils.getQualifier(clazz));

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

                placeholderStore.register(metadata);
            }
        }

        if (!papiExpansion.register()) {
            plugin.getLogger().warning("Failed to register placeholder expansion for plugin: " + plugin.getName());
        }
    }

    /**
     * Unregisters the PlaceholderAPI expansion (when registered) and clears the published placeholders.
     */
    public void unregister() {
        if (papiExpansion.isRegistered()) {
            papiExpansion.unregister();
        }
        placeholderStore.clear();
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
}
