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
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnContext;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;

import java.util.Objects;

/**
 * Immutable spawn-time context used to create the controller for one spawn.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 */
final class RuntimeSpawnContext<T extends Entity> implements SpawnContext<T>, CustomEntitySpawnContext<T> {
    private final EntityTemplate<T> template;
    private final SpawnOptions spawnOptions;
    private final MinecraftVersion minecraftVersion;

    RuntimeSpawnContext(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        this.template = Objects.requireNonNull(template, "template cannot be null");
        this.spawnOptions = Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
    }

    @Override
    public @NotNull EntityTemplate<T> template() {
        return template;
    }

    @Override
    public @NotNull CustomEntityBaseType baseType() {
        return template.baseType();
    }

    @Override
    public @NotNull Class<T> bukkitType() {
        return template.bukkitType();
    }

    @Override
    public @NotNull MinecraftVersion minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public @NotNull SpawnOptions spawnOptions() {
        return spawnOptions;
    }
}
