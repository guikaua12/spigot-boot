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

import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.ControlledZombie;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;

/**
 * Spawn-time runtime handle specialization for zombie definitions.
 *
 * @since 2.0.2
 */
public final class RuntimeNativeZombieLifecycle extends RuntimeNativeEntityLifecycle<Zombie> implements ControlledZombie {

    public RuntimeNativeZombieLifecycle(
            @NotNull CustomEntityDefinition<Zombie> definition,
            @NotNull CustomEntitySpawnRequest spawnRequest,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        super(definition, spawnRequest, minecraftVersion);
    }
}
