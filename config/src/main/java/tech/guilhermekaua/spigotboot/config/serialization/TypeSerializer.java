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
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;

/**
 * Handles conversion between configuration nodes and Java types.
 *
 * @param <T> the type this serializer handles
 */
public interface TypeSerializer<T> {

    /**
     * Deserializes a configuration node to the target type.
     *
     * @param node the source node
     * @param type the target type class
     * @return the deserialized value
     * @throws SerializationException if deserialization fails
     */
    T deserialize(@NotNull ConfigNode node, @NotNull Class<T> type) throws SerializationException;

    /**
     * Serializes a value to a configuration node.
     *
     * @param value the value to serialize
     * @param node  the target node
     * @throws SerializationException if serialization fails
     */
    void serialize(@NotNull T value, @NotNull MutableConfigNode node) throws SerializationException;

    /**
     * Checks if this serializer can handle the given type.
     * <p>
     * Used for dynamic serializer selection when the exact type
     * is not registered but a compatible serializer exists.
     *
     * @param type the type to check
     * @return true if this serializer can handle the type
     */
    default boolean canHandle(@NotNull Class<?> type) {
        return true;
    }
}
