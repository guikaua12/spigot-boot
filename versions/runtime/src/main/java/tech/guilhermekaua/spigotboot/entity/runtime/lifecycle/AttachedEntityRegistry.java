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
package tech.guilhermekaua.spigotboot.entity.runtime.lifecycle;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tracks already-attached entities by Bukkit and native identity.
 *
 * @since 2.0.2
 */
public final class AttachedEntityRegistry {
    private final Map<Entity, ControlledEntity<?>> byBukkit = new IdentityHashMap<>();
    private final Map<Object, ControlledEntity<?>> byNative = new IdentityHashMap<>();

    public synchronized @Nullable ControlledEntity<?> findByBukkit(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        return byBukkit.get(entity);
    }

    public synchronized @Nullable ControlledEntity<?> findByNative(@NotNull Object nativeEntity) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        return byNative.get(nativeEntity);
    }

    public synchronized void register(
            @NotNull Entity bukkitEntity,
            @NotNull Object nativeEntity,
            @NotNull ControlledEntity<?> controlledEntity
    ) {
        Objects.requireNonNull(bukkitEntity, "bukkitEntity cannot be null");
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        Objects.requireNonNull(controlledEntity, "controlledEntity cannot be null");
        pruneEntriesFor(bukkitEntity, controlledEntity);
        byBukkit.put(bukkitEntity, controlledEntity);
        byNative.put(nativeEntity, controlledEntity);
    }

    public synchronized void unregister(@NotNull Entity bukkitEntity, @Nullable Object nativeEntity) {
        Objects.requireNonNull(bukkitEntity, "bukkitEntity cannot be null");
        byBukkit.remove(bukkitEntity);
        if (nativeEntity != null) {
            byNative.remove(nativeEntity);
        }
    }

    private void pruneEntriesFor(
            @NotNull Entity bukkitEntity,
            @NotNull ControlledEntity<?> controlledEntity
    ) {
        ControlledEntity<?> previousBukkitEntry = byBukkit.get(bukkitEntity);
        if (previousBukkitEntry != null) {
            removeNativeEntries(previousBukkitEntry);
        }
        removeNativeEntries(controlledEntity);
    }

    private void removeNativeEntries(@NotNull ControlledEntity<?> controlledEntity) {
        java.util.Iterator<Map.Entry<Object, ControlledEntity<?>>> iterator = byNative.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, ControlledEntity<?>> entry = iterator.next();
            if (entry.getValue() == controlledEntity) {
                iterator.remove();
            }
        }
    }
}
