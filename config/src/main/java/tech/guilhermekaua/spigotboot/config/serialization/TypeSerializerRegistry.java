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

/**
 * Registry for type serializers with inheritance support.
 * <p>
 * Provides methods to register and retrieve serializers for specific types,
 * with fallback to superclass/interface serializers when exact matches aren't found.
 */
public interface TypeSerializerRegistry {

    /**
     * Registers a serializer for a specific type.
     *
     * @param type       the type to register
     * @param serializer the serializer for that type
     * @param <T>        the type parameter
     */
    <T> void register(@NotNull Class<T> type, @NotNull TypeSerializer<T> serializer);

    /**
     * Gets the serializer for an exact type match.
     *
     * @param type the type to look up
     * @param <T>  the type parameter
     * @return the serializer, or null if not found
     */
    @Nullable
    <T> TypeSerializer<T> get(@NotNull Class<T> type);

    /**
     * Gets a serializer, checking superclasses and interfaces if no exact match exists.
     *
     * @param type the type to look up
     * @param <T>  the type parameter
     * @return the serializer, or null if not found
     */
    @Nullable
    <T> TypeSerializer<T> getWithInheritance(@NotNull Class<T> type);

    /**
     * Creates a copy of this registry.
     *
     * @return a new registry with the same serializers
     */
    @NotNull
    TypeSerializerRegistry copy();

    /**
     * Creates a registry with default serializers for common types.
     *
     * @return a new registry with defaults
     */
    @NotNull
    static TypeSerializerRegistry defaults() {
        return DefaultTypeSerializerRegistry.createWithDefaults();
    }

    /**
     * Creates an empty registry.
     *
     * @return a new empty registry
     */
    @NotNull
    static TypeSerializerRegistry create() {
        return new DefaultTypeSerializerRegistry();
    }
}
