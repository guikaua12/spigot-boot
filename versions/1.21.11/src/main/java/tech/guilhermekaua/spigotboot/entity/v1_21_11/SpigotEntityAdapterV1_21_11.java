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
package tech.guilhermekaua.spigotboot.entity.v1_21_11;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.v1_21_11.zombie.ZombieFactoryV1_21_11;

import java.util.Objects;

/**
 * Native custom entity adapter for Minecraft 1.21.11.
 *
 * @since 2.0.2
 */
public final class SpigotEntityAdapterV1_21_11 implements EntityVersionAdapter {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 21, 11);

    private final ZombieFactoryV1_21_11 zombieFactory = new ZombieFactoryV1_21_11();

    @Override
    public @NotNull MinecraftVersion minimumVersion() {
        return VERSION;
    }

    @Override
    public @NotNull MinecraftVersion maximumVersion() {
        return VERSION;
    }

    @Override
    public boolean supports(@NotNull CustomEntityBaseType baseType) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return baseType == CustomEntityBaseType.ZOMBIE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends LivingEntity> @NotNull CustomEntityHandle<T> spawn(
            @NotNull CustomEntityDefinition<T> definition,
            @NotNull CustomEntitySpawnRequest spawnRequest,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(spawnRequest, "spawnRequest cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        if (definition.baseType() != CustomEntityBaseType.ZOMBIE) {
            throw new UnsupportedOperationException(
                    "Minecraft 1.21.11 custom entities currently support only zombies."
            );
        }

        return (CustomEntityHandle<T>) zombieFactory.spawn(
                definition,
                spawnRequest,
                (NativeEntityLifecycle<Zombie>) lifecycle
        );
    }
}
