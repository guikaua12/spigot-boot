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
package tech.guilhermekaua.spigotboot.config.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.serialization.scalars.PrimitiveSerializers;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link TypeSerializerRegistry}.
 */
public class DefaultTypeSerializerRegistry implements TypeSerializerRegistry {

    private final Map<Class<?>, TypeSerializer<?>> serializers = new ConcurrentHashMap<>();

    @Override
    public <T> void register(@NotNull Class<T> type, @NotNull TypeSerializer<T> serializer) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(serializer, "serializer cannot be null");
        serializers.put(type, serializer);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> TypeSerializer<T> get(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        return (TypeSerializer<T>) serializers.get(type);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> TypeSerializer<T> getWithInheritance(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");

        TypeSerializer<T> exact = get(type);
        if (exact != null) {
            return exact;
        }

        Class<?> superClass = type.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            TypeSerializer<?> serializer = serializers.get(superClass);
            if (serializer != null && serializer.canHandle(type)) {
                return (TypeSerializer<T>) serializer;
            }
            superClass = superClass.getSuperclass();
        }

        for (Class<?> iface : type.getInterfaces()) {
            TypeSerializer<?> serializer = serializers.get(iface);
            if (serializer != null && serializer.canHandle(type)) {
                return (TypeSerializer<T>) serializer;
            }
        }

        for (TypeSerializer<?> serializer : serializers.values()) {
            if (serializer.canHandle(type)) {
                return (TypeSerializer<T>) serializer;
            }
        }

        return null;
    }

    @Override
    @NotNull
    public TypeSerializerRegistry copy() {
        DefaultTypeSerializerRegistry copy = new DefaultTypeSerializerRegistry();
        copy.serializers.putAll(this.serializers);
        return copy;
    }

    /**
     * Creates a registry with default serializers.
     *
     * @return a new registry with defaults
     */
    @NotNull
    static DefaultTypeSerializerRegistry createWithDefaults() {
        DefaultTypeSerializerRegistry registry = new DefaultTypeSerializerRegistry();
        PrimitiveSerializers.registerAll(registry);
        return registry;
    }
}
