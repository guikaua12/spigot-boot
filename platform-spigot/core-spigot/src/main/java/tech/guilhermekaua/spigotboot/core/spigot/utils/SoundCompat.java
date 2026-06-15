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
package tech.guilhermekaua.spigotboot.core.spigot.utils;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Cross-version {@link Sound} resolution. {@code Sound} is an enum up to MC 1.21.2 and an
 * interface (registry type) from 1.21.3 on; this resolves a sound from a config string and
 * back without a compile-time assumption about its shape.
 */
public final class SoundCompat {

    private SoundCompat() {
    }

    /**
     * Resolves a sound from a config value — an enum constant name (e.g.
     * {@code ENTITY_PLAYER_LEVELUP}) or a namespaced key (e.g.
     * {@code minecraft:entity.player.levelup}).
     *
     * <p>Note: bare enum-constant names (e.g. {@code ENTITY_PLAYER_LEVELUP}) are only accepted on
     * enum-{@code Sound} servers (MC &lt;= 1.21.2); on interface-{@code Sound} servers (&gt;= 1.21.3)
     * supply a namespaced key (e.g. {@code minecraft:entity.player.levelup}).
     *
     * @param value the config value
     * @return the resolved sound, or {@code null} if unknown
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @Nullable Sound resolve(@NotNull String value) {
        String trimmed = value.trim();
        if (Sound.class.isEnum()) {
            String name = trimmed.toUpperCase().replace('.', '_').replace(' ', '_').replace(':', '_');
            // strip a namespace prefix like MINECRAFT_ if present
            if (name.startsWith("MINECRAFT_")) {
                name = name.substring("MINECRAFT_".length());
            }
            try {
                return (Sound) Enum.valueOf((Class) Sound.class, name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return resolveFromRegistry(trimmed);
    }

    /**
     * Serializes a sound back to a stable config string: the enum name on enum builds, else the
     * namespaced key (e.g. {@code minecraft:entity.player.levelup}).
     *
     * @param sound the sound
     * @return the config string
     */
    public static @NotNull String toKey(@NotNull Sound sound) {
        if (sound instanceof Enum) {
            // cast via Object so the compiler emits a checkcast: Sound is an enum on the compile
            // classpath, so a direct (Enum) cast would be a no-op upcast and the bytecode would
            // invokevirtual Enum.name() on a Sound operand — a VerifyError on servers (>=1.21.3)
            // where Sound is an interface, even though this branch never runs there.
            return ((Enum<?>) (Object) sound).name();
        }
        try {
            Method getKey = sound.getClass().getMethod("getKey");
            Object key = getKey.invoke(sound); // NamespacedKey
            return String.valueOf(key);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read sound key", e);
        }
    }

    private static @Nullable Sound resolveFromRegistry(String value) {
        try {
            Class<?> namespacedKey = Class.forName("org.bukkit.NamespacedKey");
            Method fromString = namespacedKey.getMethod("fromString", String.class);
            Object key = fromString.invoke(null, value.toLowerCase());
            if (key == null) {
                return null;
            }
            Method getRegistry = Bukkit.class.getMethod("getRegistry", Class.class);
            Object registry = getRegistry.invoke(null, Sound.class);
            if (registry == null) {
                return null;
            }
            Class<?> registryInterface = Class.forName("org.bukkit.Registry");
            Method get = registryInterface.getMethod("get", namespacedKey);
            return (Sound) get.invoke(registry, key);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
