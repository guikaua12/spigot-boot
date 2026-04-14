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
package tech.guilhermekaua.spigotboot.entity.runtime.publication;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityPublicationFamily;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Shared base implementation for explicit publication backends.
 *
 * @since 2.0.2
 */
abstract class AbstractEntityPublicationBackend implements EntityPublicationBackend {
    private static final CreatureSpawnEvent.SpawnReason CUSTOM_SPAWN_REASON = CreatureSpawnEvent.SpawnReason.CUSTOM;

    private final EntityPublicationFamily family;
    private final String[] addMethodNames;

    protected AbstractEntityPublicationBackend(
            @NotNull EntityPublicationFamily family,
            @NotNull String[] addMethodNames
    ) {
        this.family = Objects.requireNonNull(family, "family cannot be null");
        this.addMethodNames = Objects.requireNonNull(addMethodNames, "addMethodNames cannot be null");
    }

    @Override
    public final @NotNull EntityPublicationFamily family() {
        return family;
    }

    @Override
    public void addFreshEntity(
            @NotNull EntityPublicationFreshSupport support,
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
        Method addMethod = ReflectionSupport.findCompatibleMethod(levelHandle.getClass(), addMethodNames, nativeEntity.getClass());
        Object result;
        if (addMethod != null) {
            result = ReflectionSupport.invoke(addMethod, levelHandle, nativeEntity);
        } else {
            Method reasonedAddMethod = ReflectionSupport.findCompatibleMethod(
                    levelHandle.getClass(),
                    addMethodNames,
                    nativeEntity.getClass(),
                    CreatureSpawnEvent.SpawnReason.class
            );
            if (reasonedAddMethod == null) {
                throw new IllegalStateException(
                        "Could not resolve an entity add method for publication backend '"
                                + family.id()
                                + "' on native world type '"
                                + levelHandle.getClass().getName()
                                + "'."
                );
            }
            result = ReflectionSupport.invoke(reasonedAddMethod, levelHandle, nativeEntity, CUSTOM_SPAWN_REASON);
        }

        if (result instanceof Boolean && !((Boolean) result).booleanValue()) {
            throw new IllegalStateException(
                    "Minecraft rejected the generated native entity during publication family '"
                            + family.id()
                            + "' world add."
            );
        }
    }

    @Override
    public void publishReplacement(
            @NotNull EntityPublicationReplacementSupport support,
            @NotNull Entity entity,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(support, "support cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");

        support.rebindBukkitEntity(entity, replacementHandle);
        support.rebindBukkitBridge(family, entity, currentNativeHandle, replacementHandle);
        support.replaceWorldReferences(family, currentNativeHandle, replacementHandle);
        support.rewireVehicleAndPassengerReferences(family, currentNativeHandle, replacementHandle);
        support.refreshBukkitWrappers(family, entity);
        support.markEntityRemoved(family, currentNativeHandle);
    }
}
