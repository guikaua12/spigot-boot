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
package tech.guilhermekaua.spigotboot.entity.runtime.strategy;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Shared world-add strategy used by paper-like 1.21+ runtimes.
 *
 * @since 2.0.2
 */
public final class PaperWorldAddStrategy_1_21_plus implements WorldAddStrategy {
    private final String id;

    /**
     * Creates a new shared paper-like world-add strategy.
     *
     * @param id the runtime-selected strategy id
     */
    public PaperWorldAddStrategy_1_21_plus(@NotNull String id) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull EntityWorldRegistrationMode freshSpawnWorldRegistrationMode() {
        return EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD;
    }

    @Override
    public @NotNull EntityWorldRegistrationMode replacementWorldRegistrationMode() {
        return EntityWorldRegistrationMode.REFERENCE_REWRITE;
    }

    /**
     * Publishes a fresh paper-like native entity into the target world.
     *
     * @param support the version-local support bridge
     * @param nativeEntity the native entity to add
     * @param location the spawn location
     */
    public void addFreshEntity(
            @NotNull FreshSupport support,
            @NotNull Object nativeEntity,
            @NotNull Location location
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        Objects.requireNonNull(location, "location cannot be null");

        support.beforeWorldAdd(nativeEntity, location);
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        world.getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);

        Object levelHandle = support.resolveNativeWorldHandle(location);
        Method addMethod = ReflectionSupport.findCompatibleMethod(
                levelHandle.getClass(),
                new String[]{"addFreshEntity"},
                nativeEntity.getClass()
        );
        if (addMethod == null) {
            addMethod = ReflectionSupport.findCompatibleMethod(
                    levelHandle.getClass(),
                    new String[]{"addWithUUID"},
                    nativeEntity.getClass()
            );
        }
        if (addMethod == null) {
            addMethod = ReflectionSupport.findCompatibleMethod(
                    levelHandle.getClass(),
                    new String[]{"addEntity"},
                    nativeEntity.getClass()
            );
        }
        if (addMethod == null) {
            throw new IllegalStateException(
                    "Could not resolve an entity add method for native world type '"
                            + levelHandle.getClass().getName()
                            + "'."
            );
        }

        Object result = ReflectionSupport.invoke(addMethod, levelHandle, nativeEntity);
        if (result instanceof Boolean && !((Boolean) result).booleanValue()) {
            throw new IllegalStateException("Minecraft rejected the generated native entity during world add.");
        }
    }

    /**
     * Publishes a paper-like replacement through the era-specific rewrite order.
     *
     * @param support the version-local support bridge
     * @param entity the Bukkit entity being replaced
     * @param currentNativeHandle the current native handle
     * @param replacementHandle the replacement native handle
     */
    public void publishReplacement(
            @NotNull ReplacementSupport support,
            @NotNull Entity entity,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");

        support.rebindBukkitZombie(entity, replacementHandle);
        support.rebindModernBukkitBridge(entity, currentNativeHandle, replacementHandle);
        support.replaceModernWorldReferences(currentNativeHandle, replacementHandle);
        support.rewireModernVehicleAndPassengerReferences(currentNativeHandle, replacementHandle);
        support.refreshModernBukkitWrappers(entity);
        support.markModernEntityRemoved(currentNativeHandle);
    }

    /**
     * Retargets an existing modern section callback when possible.
     *
     * @param oldLevelCallback the existing callback
     * @param oldHandle the original entity handle
     * @param replacementHandle the replacement entity handle
     * @return the rebound callback, or {@code null}
     */
    public static @Nullable Object retargetModernSectionCallback(
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
     * Recreates a modern section callback when it cannot be retargeted in place.
     *
     * @param oldLevelCallback the existing callback
     * @param replacementHandle the replacement entity handle
     * @return the recreated callback, or {@code null}
     */
    public static @Nullable Object recreateModernSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        if (oldLevelCallback == null) {
            return null;
        }

        Field entityField = ReflectionSupport.findField(oldLevelCallback.getClass(), "entity");
        Field managerField = ReflectionSupport.findField(oldLevelCallback.getClass(), "this$0");
        Field currentSectionKeyField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSectionKey");
        Field currentSectionField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSection");
        if (entityField == null || managerField == null || currentSectionKeyField == null || currentSectionField == null) {
            return null;
        }

        Object manager = ReflectionSupport.readField(managerField, oldLevelCallback);
        Object currentSectionKey = ReflectionSupport.readField(currentSectionKeyField, oldLevelCallback);
        Object currentSection = ReflectionSupport.readField(currentSectionField, oldLevelCallback);
        if (!(currentSectionKey instanceof Long)) {
            return null;
        }

        Constructor<?> callbackConstructor = findModernSectionCallbackConstructor(
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
     * Migrates modern section membership from the old handle to the replacement handle.
     *
     * @param oldLevelCallback the existing callback
     * @param oldHandle the original entity handle
     * @param replacementHandle the replacement entity handle
     */
    public static void migrateModernSectionMembership(
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
     * Replaces an entry in a managed collection when the original element is present.
     *
     * @param collection the managed collection
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

    private static @Nullable Constructor<?> findModernSectionCallbackConstructor(
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

    /**
     * Version-local support bridge for paper-like fresh-spawn world add.
     *
     * @since 2.0.2
     */
    public interface FreshSupport {

        /**
         * Hook invoked immediately before the shared world-add sequence runs.
         *
         * @param nativeEntity the native entity being added
         * @param location the target location
         */
        default void beforeWorldAdd(@NotNull Object nativeEntity, @NotNull Location location) {
        }

        /**
         * Resolves the paper-like native world handle for the supplied location.
         *
         * @param location the target location
         * @return the native world handle
         */
        @NotNull Object resolveNativeWorldHandle(@NotNull Location location);
    }

    /**
     * Version-local support bridge for paper-like replacement publication.
     *
     * @since 2.0.2
     */
    public interface ReplacementSupport {

        /**
         * Rebinds the Bukkit wrapper to point at the replacement handle.
         *
         * @param entity the Bukkit entity wrapper
         * @param replacementHandle the replacement handle
         */
        void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle);

        /**
         * Rebinds the paper-like Bukkit bridge fields to the replacement handle.
         *
         * @param bukkitEntity the Bukkit wrapper
         * @param oldHandle the original native handle
         * @param replacementHandle the replacement native handle
         */
        void rebindModernBukkitBridge(
                @NotNull Entity bukkitEntity,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        );

        /**
         * Rewrites paper-like world registries from the old handle to the replacement handle.
         *
         * @param oldHandle the original native handle
         * @param replacementHandle the replacement native handle
         */
        void replaceModernWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle);

        /**
         * Rewrites vehicle and passenger references to the replacement handle.
         *
         * @param oldHandle the original native handle
         * @param replacementHandle the replacement native handle
         */
        void rewireModernVehicleAndPassengerReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle);

        /**
         * Refreshes cached Bukkit wrappers after the replacement is published.
         *
         * @param entity the Bukkit entity wrapper
         */
        void refreshModernBukkitWrappers(@NotNull Entity entity);

        /**
         * Marks the original paper-like native handle as removed.
         *
         * @param oldHandle the original native handle
         */
        void markModernEntityRemoved(@NotNull Object oldHandle);
    }

    private static @NotNull Object immutablePassengerList(@NotNull List<Object> passengers) {
        Class<?> immutableListClass = ReflectionSupport.requireClass("com.google.common.collect.ImmutableList");
        Method copyOfMethod = ReflectionSupport.requireCompatibleMethod(
                immutableListClass,
                new String[]{"copyOf"},
                java.util.Collection.class
        );
        return ReflectionSupport.invoke(copyOfMethod, null, passengers);
    }
}
