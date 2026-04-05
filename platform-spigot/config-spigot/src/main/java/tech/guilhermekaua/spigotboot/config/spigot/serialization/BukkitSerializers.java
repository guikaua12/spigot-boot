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

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility class to register all Bukkit-specific type serializers.
 */
public final class BukkitSerializers {

    private static final String PARTICLE_CLASS_NAME = "org.bukkit.Particle";

    private BukkitSerializers() {
    }

    /**
     * Registers all Bukkit serializers to the given registry.
     *
     * @param registry the registry to populate
     */
    public static void registerAll(@NotNull TypeSerializerRegistry registry) {
        registerAll(registry, BukkitSerializers::resolveOptionalType);
    }

    static void registerAll(
            @NotNull TypeSerializerRegistry registry,
            @NotNull OptionalTypeResolver optionalTypeResolver
    ) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(optionalTypeResolver, "optionalTypeResolver cannot be null");

        registry.register(Material.class, new MaterialSerializer());
        registry.register(Sound.class, new SoundSerializer());
        registry.register(World.class, new WorldSerializer());
        registry.register(Location.class, new LocationSerializer());
        registry.register(Duration.class, new DurationSerializer());

        registerOptionalEnumSerializer(registry, PARTICLE_CLASS_NAME, optionalTypeResolver);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerOptionalEnumSerializer(
            @NotNull TypeSerializerRegistry registry,
            @NotNull String className,
            @NotNull OptionalTypeResolver optionalTypeResolver
    ) {
        Class<?> rawType = optionalTypeResolver.resolve(className);
        if (rawType == null || !rawType.isEnum()) {
            return;
        }

        registry.register((Class) rawType, new EnumNameSerializer(rawType));
    }

    private static @Nullable Class<?> resolveOptionalType(@NotNull String className) {
        try {
            return Class.forName(className, false, BukkitSerializers.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    @FunctionalInterface
    interface OptionalTypeResolver {
        @Nullable Class<?> resolve(@NotNull String className);
    }

    private static final class EnumNameSerializer<T extends Enum<T>> implements TypeSerializer<T> {

        private final Class<T> enumType;

        private EnumNameSerializer(@NotNull Class<T> enumType) {
            this.enumType = Objects.requireNonNull(enumType, "enumType cannot be null");
        }

        @Override
        public T deserialize(@NotNull ConfigNode node, @NotNull Class<T> type) throws SerializationException {
            String value = node.get(String.class);
            if (value == null || value.isEmpty()) {
                return null;
            }

            try {
                return Enum.valueOf(enumType, normalize(value));
            } catch (IllegalArgumentException exception) {
                throw new SerializationException(
                        "Unknown " + enumType.getSimpleName().toLowerCase(Locale.ROOT) + ": " + value,
                        exception
                );
            }
        }

        @Override
        public void serialize(@NotNull T value, @NotNull MutableConfigNode node) throws SerializationException {
            node.set(value.name());
        }

        @Override
        public boolean canHandle(@NotNull Class<?> type) {
            return enumType.equals(type);
        }

        private @NotNull String normalize(@NotNull String value) {
            return value.trim().toUpperCase(Locale.ROOT).replace(" ", "_").replace(".", "_");
        }
    }
}
