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
package tech.guilhermekaua.spigotboot.config.spigot.serialization;

import org.bukkit.*;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;

import java.time.Duration;
import java.util.Objects;

/**
 * Utility class to register all Bukkit-specific type serializers.
 */
public final class BukkitSerializers {

    private BukkitSerializers() {
    }

    /**
     * Registers all Bukkit serializers to the given registry.
     *
     * @param registry the registry to populate
     */
    public static void registerAll(@NotNull TypeSerializerRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");

        registry.register(Material.class, new MaterialSerializer());
        registry.register(Sound.class, new SoundSerializer());
        registry.register(Particle.class, new ParticleSerializer());
        registry.register(World.class, new WorldSerializer());
        registry.register(Location.class, new LocationSerializer());
        registry.register(Duration.class, new DurationSerializer());
    }
}
