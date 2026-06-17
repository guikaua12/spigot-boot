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
package tech.guilhermekaua.spigotboot.config.injector;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfigName;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigRef;
import tech.guilhermekaua.spigotboot.config.folder.FolderConfigSnapshot;
import tech.guilhermekaua.spigotboot.config.DefaultConfigManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

/**
 * Custom injector for {@link FolderConfigRef} injection.
 * <p>
 * This injector enables dependency injection for folder configs without
 * requiring @Inject annotation. It resolves {@code FolderConfigRef<T>} types
 * and uses {@link FolderConfigName} for disambiguation when multiple folder configs of
 * the same item type exist.
 * <p>
 * Supported injection types:
 * <ul>
 *   <li>{@code FolderConfigRef<T>}</li>
 *   <li>{@code FolderConfigSnapshot<T>} (returns current snapshot)</li>
 * </ul>
 */
public class FolderConfigInjector implements CustomInjector {

    private final DefaultConfigManager configManager;

    /**
     * Creates a new folder config injector.
     *
     * @param configManager the config manager
     */
    public FolderConfigInjector(@NotNull DefaultConfigManager configManager) {
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
    }

    @Override
    public boolean supports(@NotNull InjectionPoint injectionPoint) {
        Class<?> rawType = injectionPoint.getRawType();
        if (rawType == null) {
            return false;
        }

        return rawType == FolderConfigRef.class || rawType == FolderConfigSnapshot.class;
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

        String folderConfigName = resolveFolderConfigName(injectionPoint.getAnnotatedElement(), itemType);
        if (folderConfigName == null) {
            return InjectionResult.notHandled();
        }

        try {
            if (rawType == FolderConfigRef.class) {
                FolderConfigRef<?> ref = configManager.getFolderConfigRef(itemType, folderConfigName);
                return InjectionResult.handled(ref);
            } else if (rawType == FolderConfigSnapshot.class) {
                FolderConfigRef<?> ref = configManager.getFolderConfigRef(itemType, folderConfigName);
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
     * Extracts the item type from a parameterized type like {@code FolderConfigRef<T>}.
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
            // nested generic like FolderConfigRef<List<String>>
            Type rawType = ((ParameterizedType) itemType).getRawType();
            if (rawType instanceof Class) {
                return (Class<?>) rawType;
            }
        }

        return null;
    }

    /**
     * Resolves the folder config name from the annotated element.
     *
     * @param element  the annotated element (field, parameter, or method)
     * @param itemType the item type
     * @return the folder config name, or null if cannot be resolved
     */
    private String resolveFolderConfigName(AnnotatedElement element, Class<?> itemType) {
        FolderConfigName refName = element.getAnnotation(FolderConfigName.class);
        if (refName != null && !refName.value().isEmpty()) {
            return refName.value();
        }

        Set<String> names = configManager.getFolderConfigNames(itemType);
        if (names.isEmpty()) {
            return null;
        }
        if (names.size() == 1) {
            return names.iterator().next();
        }

        return null;
    }
}
