/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.versions.runtime.publication;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Shared reflective utilities used by section-manager and chunk-system publication bridges.
 *
 * @since 2.0.2
 */
public final class SectionManagerPublicationSupport {

    private SectionManagerPublicationSupport() {
    }

    /**
     * Retargets an existing section callback when the callback stores the tracked entity directly.
     *
     * @param oldLevelCallback the existing callback
     * @param oldHandle the original entity handle
     * @param replacementHandle the replacement entity handle
     * @return the rebound callback, or {@code null}
     */
    public static @Nullable Object retargetSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        if (oldLevelCallback == null) {
            return null;
        }

        Field entityField = ReflectionSupport.findField(oldLevelCallback.getClass(), "entity");
        if (entityField == null) {
            return null;
        }

        Object callbackEntity = ReflectionSupport.readField(entityField, oldLevelCallback);
        if (callbackEntity != oldHandle) {
            return null;
        }
        if (!entityField.getType().isAssignableFrom(replacementHandle.getClass())) {
            return null;
        }

        ReflectionSupport.writeField(entityField, oldLevelCallback, replacementHandle);
        return oldLevelCallback;
    }

    /**
     * Recreates a section callback when it cannot be safely retargeted in place.
     *
     * @param oldLevelCallback the existing callback
     * @param replacementHandle the replacement entity handle
     * @return the rebound callback, or {@code null}
     */
    public static @Nullable Object recreateSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        if (oldLevelCallback == null) {
            return null;
        }

        Field managerField = ReflectionSupport.findField(oldLevelCallback.getClass(), "this$0");
        Field currentSectionKeyField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSectionKey");
        Field currentSectionField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSection");
        if (managerField == null || currentSectionKeyField == null || currentSectionField == null) {
            return null;
        }

        Object manager = ReflectionSupport.readField(managerField, oldLevelCallback);
        Object currentSectionKey = ReflectionSupport.readField(currentSectionKeyField, oldLevelCallback);
        Object currentSection = ReflectionSupport.readField(currentSectionField, oldLevelCallback);
        if (!(currentSectionKey instanceof Long)) {
            return null;
        }

        Constructor<?> callbackConstructor = findSectionCallbackConstructor(
                oldLevelCallback.getClass(),
                manager,
                replacementHandle,
                currentSection
        );
        if (callbackConstructor == null) {
            return null;
        }

        return ReflectionSupport.instantiate(
                callbackConstructor,
                manager,
                replacementHandle,
                Long.valueOf(((Long) currentSectionKey).longValue()),
                currentSection
        );
    }

    /**
     * Migrates section membership from the original entity handle to the replacement handle.
     *
     * @param oldLevelCallback the existing callback
     * @param oldHandle the original entity handle
     * @param replacementHandle the replacement entity handle
     */
    public static void migrateSectionMembership(
            @Nullable Object oldLevelCallback,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        if (oldLevelCallback == null) {
            return;
        }

        Field currentSectionField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSection");
        if (currentSectionField == null) {
            return;
        }

        Object currentSection = ReflectionSupport.readField(currentSectionField, oldLevelCallback);
        if (currentSection == null) {
            return;
        }

        Method removeMethod = ReflectionSupport.findCompatibleMethod(
                currentSection.getClass(),
                new String[]{"remove"},
                oldHandle.getClass()
        );
        Method addMethod = ReflectionSupport.findCompatibleMethod(
                currentSection.getClass(),
                new String[]{"add"},
                replacementHandle.getClass()
        );
        if (removeMethod == null || addMethod == null) {
            return;
        }

        boolean removed = Boolean.TRUE.equals(ReflectionSupport.invoke(removeMethod, currentSection, oldHandle));
        if (removed || !containsManagedEntry(currentSection, replacementHandle)) {
            ReflectionSupport.invoke(addMethod, currentSection, replacementHandle);
        }
    }

    /**
     * Replaces an entry in one managed collection when the original entry is still present.
     *
     * @param collection the collection to update
     * @param oldValue the original value
     * @param newValue the replacement value
     */
    public static void replaceManagedCollectionEntry(
            @Nullable Object collection,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        Objects.requireNonNull(oldValue, "oldValue cannot be null");
        Objects.requireNonNull(newValue, "newValue cannot be null");
        if (collection == null || !containsManagedEntry(collection, oldValue)) {
            return;
        }

        Method removeMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"remove"},
                oldValue.getClass()
        );
        Method addMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"add"},
                newValue.getClass()
        );
        if (removeMethod != null && addMethod != null) {
            ReflectionSupport.invoke(removeMethod, collection, oldValue);
            ReflectionSupport.invoke(addMethod, collection, newValue);
            return;
        }

        if (collection instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> values = (Collection<Object>) collection;
            if (values.remove(oldValue)) {
                values.add(newValue);
            }
        }
    }

    private static @Nullable Constructor<?> findSectionCallbackConstructor(
            @NotNull Class<?> callbackType,
            @Nullable Object manager,
            @NotNull Object replacementHandle,
            @Nullable Object currentSection
    ) {
        for (Constructor<?> constructor : callbackType.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 4) {
                continue;
            }
            if (manager == null || !parameterTypes[0].isAssignableFrom(manager.getClass())) {
                continue;
            }
            if (!parameterTypes[1].isAssignableFrom(replacementHandle.getClass())) {
                continue;
            }
            if (!(parameterTypes[2] == long.class || parameterTypes[2] == Long.class)) {
                continue;
            }
            if (currentSection != null && !parameterTypes[3].isAssignableFrom(currentSection.getClass())) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor;
        }
        return null;
    }

    private static boolean containsManagedEntry(@Nullable Object collection, @NotNull Object value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (collection == null) {
            return false;
        }

        Method containsMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"contains"},
                value.getClass()
        );
        if (containsMethod != null) {
            Object result = ReflectionSupport.invoke(containsMethod, collection, value);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        }

        if (collection instanceof Collection) {
            return ((Collection<?>) collection).contains(value);
        }

        Method getEntitiesMethod = ReflectionSupport.findNamedMethod(collection.getClass(), new String[]{"getEntities"});
        if (getEntitiesMethod == null) {
            return false;
        }

        Object entities = ReflectionSupport.invoke(getEntitiesMethod, collection);
        if (entities instanceof Stream) {
            try (Stream<?> stream = (Stream<?>) entities) {
                return stream.anyMatch(candidate -> candidate == value);
            }
        }
        if (entities instanceof Iterable) {
            for (Object candidate : (Iterable<?>) entities) {
                if (candidate == value) {
                    return true;
                }
            }
        }
        return false;
    }
}
