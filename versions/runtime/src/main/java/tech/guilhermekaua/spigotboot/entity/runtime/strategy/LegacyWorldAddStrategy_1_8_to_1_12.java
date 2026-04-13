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
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Shared world-add strategy used by legacy 1.8-1.12 runtimes.
 *
 * @since 2.0.2
 */
public final class LegacyWorldAddStrategy_1_8_to_1_12 implements WorldAddStrategy {
    private final String id;

    /**
     * Creates a new shared legacy world-add strategy.
     *
     * @param id the runtime-selected strategy id
     */
    public LegacyWorldAddStrategy_1_8_to_1_12(@NotNull String id) {
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
     * Publishes a fresh legacy native entity into the target world.
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
                new String[]{"addEntity"},
                nativeEntity.getClass()
        );
        if (addMethod == null) {
            throw new IllegalStateException(
                    "Could not resolve an entity add method for legacy native world type '"
                            + levelHandle.getClass().getName()
                            + "'."
            );
        }

        Object result = ReflectionSupport.invoke(addMethod, levelHandle, nativeEntity);
        if (result instanceof Boolean && !((Boolean) result).booleanValue()) {
            throw new IllegalStateException("Minecraft rejected the generated legacy native entity during world add.");
        }
    }

    /**
     * Publishes a legacy replacement through the era-specific rewrite order.
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
        support.rebindLegacyBukkitBridge(entity, currentNativeHandle, replacementHandle);
        support.replaceLegacyWorldReferences(currentNativeHandle, replacementHandle);
        support.rewireLegacyVehicleAndPassengerReferences(currentNativeHandle, replacementHandle);
        support.refreshLegacyBukkitWrappers(entity);
        support.markLegacyEntityRemoved(currentNativeHandle);
    }

    /**
     * Version-local support bridge for legacy fresh-spawn world add.
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
         * Resolves the legacy native world handle for the supplied location.
         *
         * @param location the target location
         * @return the native world handle
         */
        @NotNull Object resolveNativeWorldHandle(@NotNull Location location);
    }

    /**
     * Version-local support bridge for legacy replacement publication.
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
         * Rebinds the legacy Bukkit bridge fields to the replacement handle.
         *
         * @param bukkitEntity the Bukkit wrapper
         * @param oldHandle the original native handle
         * @param replacementHandle the replacement native handle
         */
        void rebindLegacyBukkitBridge(
                @NotNull Entity bukkitEntity,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        );

        /**
         * Rewrites legacy world registries from the old handle to the replacement handle.
         *
         * @param oldHandle the original native handle
         * @param replacementHandle the replacement native handle
         */
        void replaceLegacyWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle);

        /**
         * Rewrites vehicle and passenger references to the replacement handle.
         *
         * @param oldHandle the original native handle
         * @param replacementHandle the replacement native handle
         */
        void rewireLegacyVehicleAndPassengerReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle);

        /**
         * Refreshes cached Bukkit wrappers after the replacement is published.
         *
         * @param entity the Bukkit entity wrapper
         */
        void refreshLegacyBukkitWrappers(@NotNull Entity entity);

        /**
         * Marks the original legacy native handle as removed.
         *
         * @param oldHandle the original native handle
         */
        void markLegacyEntityRemoved(@NotNull Object oldHandle);
    }
}
