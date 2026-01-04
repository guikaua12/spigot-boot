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
package tech.guilhermekaua.spigotboot.config.spigot.injector;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigRefName;
import tech.guilhermekaua.spigotboot.config.collection.ConfigCollectionRef;
import tech.guilhermekaua.spigotboot.config.collection.ConfigCollectionSnapshot;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

/**
 * Custom injector for ConfigCollectionRef injection.
 * <p>
 * This injector enables dependency injection for config collections without
 * requiring @Inject annotation. It resolves ConfigCollectionRef&lt;T&gt; types
 * and uses @ConfigRefName for disambiguation when multiple collections of
 * the same item type exist.
 * <p>
 * Supported injection types:
 * <ul>
 *   <li>{@code ConfigCollectionRef<T>}</li>
 *   <li>{@code ConfigCollectionSnapshot<T>} (returns current snapshot)</li>
 * </ul>
 */
public class ConfigCollectionInjector implements CustomInjector {

    private final SpigotConfigManager configManager;

    /**
     * Creates a new config collection injector.
     *
     * @param configManager the config manager
     */
    public ConfigCollectionInjector(@NotNull SpigotConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
    }

    @Override
    public boolean supports(@NotNull InjectionPoint injectionPoint) {
        Class<?> rawType = injectionPoint.getRawType();
        if (rawType == null) {
            return false;
        }

        return rawType == ConfigCollectionRef.class || rawType == ConfigCollectionSnapshot.class;
    }

    @Override
    public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
        Type type = injectionPoint.getType();
        Class<?> rawType = injectionPoint.getRawType();

        if (rawType == null) {
            return InjectionResult.notHandled();
        }

        Class<?> itemType = extractItemType(type);
        if (itemType == null) {
            return InjectionResult.notHandled();
        }

        String collectionName = resolveCollectionName(injectionPoint.getAnnotatedElement(), itemType);
        if (collectionName == null) {
            return InjectionResult.notHandled();
        }

        try {
            if (rawType == ConfigCollectionRef.class) {
                ConfigCollectionRef<?> ref = configManager.getCollectionRef(itemType, collectionName);
                return InjectionResult.handled(ref);
            } else if (rawType == ConfigCollectionSnapshot.class) {
                ConfigCollectionRef<?> ref = configManager.getCollectionRef(itemType, collectionName);
                return InjectionResult.handled(ref.get());
            }
        } catch (Exception e) {
            return InjectionResult.notHandled();
        }

        return InjectionResult.notHandled();
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * Extracts the item type from a parameterized type like ConfigCollectionRef&lt;T&gt;.
     *
     * @param type the type
     * @return the item type class, or null if not extractable
     */
    private Class<?> extractItemType(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        ParameterizedType paramType = (ParameterizedType) type;
        Type[] typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length == 0) {
            return null;
        }

        Type itemType = typeArgs[0];
        if (itemType instanceof Class) {
            return (Class<?>) itemType;
        } else if (itemType instanceof ParameterizedType) {
            // nested generic like ConfigCollectionRef<List<String>>
            Type rawType = ((ParameterizedType) itemType).getRawType();
            if (rawType instanceof Class) {
                return (Class<?>) rawType;
            }
        }

        return null;
    }

    /**
     * Resolves the collection name from the annotated element.
     *
     * @param element  the annotated element (field, parameter, or method)
     * @param itemType the item type
     * @return the collection name, or null if cannot be resolved
     */
    private String resolveCollectionName(AnnotatedElement element, Class<?> itemType) {
        ConfigRefName refName = element.getAnnotation(ConfigRefName.class);
        if (refName != null && !refName.value().isEmpty()) {
            return refName.value();
        }

        Set<String> names = configManager.getCollectionNames(itemType);
        if (names.isEmpty()) {
            return null;
        }
        if (names.size() == 1) {
            return names.iterator().next();
        }

        return null;
    }
}
