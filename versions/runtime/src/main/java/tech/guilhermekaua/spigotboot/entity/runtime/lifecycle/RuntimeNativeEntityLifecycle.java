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

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBehavior;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityState;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime bridge shared between the version-agnostic API and the real native entity subclasses.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class RuntimeNativeEntityLifecycle<T extends LivingEntity>
        implements CustomEntityHandle<T>, NativeEntityLifecycle<T> {
    private final CustomEntityDefinition<T> definition;
    private final CustomEntitySpawnRequest spawnRequest;
    private final MinecraftVersion minecraftVersion;
    private final CustomEntityBehavior<T> behavior;
    private final SimpleCustomEntityState state;
    private final AtomicBoolean removed;
    private final AtomicBoolean spawned;

    private volatile T bukkitEntity;

    public RuntimeNativeEntityLifecycle(
            @NotNull CustomEntityDefinition<T> definition,
            @NotNull CustomEntitySpawnRequest spawnRequest,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        this.definition = Objects.requireNonNull(definition, "definition cannot be null");
        this.spawnRequest = Objects.requireNonNull(spawnRequest, "spawnRequest cannot be null");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        this.state = new SimpleCustomEntityState();
        this.removed = new AtomicBoolean(false);
        this.spawned = new AtomicBoolean(false);
        this.behavior = definition.behaviorFactory().create(this);
    }

    @Override
    public void bind(@NotNull T bukkitEntity) {
        Objects.requireNonNull(bukkitEntity, "bukkitEntity cannot be null");
        if (this.bukkitEntity != null) {
            throw new IllegalStateException("The Bukkit entity has already been bound.");
        }
        this.bukkitEntity = bukkitEntity;
    }

    @Override
    public void onSpawn() {
        if (!spawned.compareAndSet(false, true)) {
            return;
        }
        ensureBound();
        definition.initializer().initialize(this);
        if (!removed.get()) {
            behavior.onSpawn(this);
        }
        removeIfRequested();
    }

    @Override
    public void onNativeTick() {
        if (removed.get()) {
            return;
        }

        T entity = bukkitEntity;
        if (entity == null) {
            return;
        }
        if (!entity.isValid() || entity.isDead()) {
            onNativeRemove();
            return;
        }

        behavior.onTick(this);
        removeIfRequested();
    }

    @Override
    public void onNativeRemove() {
        if (!removed.compareAndSet(false, true)) {
            return;
        }

        if (bukkitEntity != null) {
            behavior.onRemove(this);
        }
    }

    @Override
    public @NotNull CustomEntityHandle<T> handle() {
        return this;
    }

    @Override
    public @NotNull T bukkitEntity() {
        return ensureBound();
    }

    @Override
    public @NotNull CustomEntityState state() {
        return state;
    }

    @Override
    public void remove() {
        if (removed.get()) {
            return;
        }
        T entity = ensureBound();
        try {
            entity.remove();
        } finally {
            onNativeRemove();
        }
    }

    @Override
    public boolean isRemoved() {
        if (removed.get()) {
            return true;
        }
        T entity = bukkitEntity;
        return entity != null && (!entity.isValid() || entity.isDead());
    }

    @Override
    public @NotNull CustomEntityId definitionId() {
        return definition.id();
    }

    @Override
    public @NotNull CustomEntityBaseType baseType() {
        return definition.baseType();
    }

    @Override
    public @NotNull Class<T> bukkitType() {
        return definition.bukkitType();
    }

    @Override
    public @NotNull MinecraftVersion minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public @NotNull CustomEntitySpawnRequest spawnRequest() {
        return spawnRequest;
    }

    private @NotNull T ensureBound() {
        T entity = bukkitEntity;
        if (entity == null) {
            throw new IllegalStateException("The Bukkit entity has not been bound yet.");
        }
        return entity;
    }

    private void removeIfRequested() {
        if (!removed.get()) {
            return;
        }
        T entity = bukkitEntity;
        if (entity != null && entity.isValid() && !entity.isDead()) {
            entity.remove();
        }
    }
}
