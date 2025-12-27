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
package tech.guilhermekaua.spigotboot.config.binding;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.core.validation.Validator;

/**
 * Binds config nodes to Java objects.
 * <p>
 * Supports both field injection and constructor binding.
 */
public interface Binder {

    /**
     * Binds a config node to a new instance of the target type.
     *
     * @param node the source node
     * @param type the target type
     * @param <T>  the type parameter
     * @return the binding result
     */
    <T> @NotNull BindingResult<T> bind(@NotNull ConfigNode node, @NotNull Class<T> type);

    /**
     * Binds config values onto an existing instance (field injection only).
     *
     * @param node     the source node
     * @param instance the target instance
     * @param <T>      the type parameter
     * @return the binding result
     */
    <T> @NotNull BindingResult<T> bind(@NotNull ConfigNode node, @NotNull T instance);

    /**
     * Serializes an object to a config node.
     *
     * @param object the object to serialize
     * @param node   the target node
     * @param <T>    the type parameter
     */
    <T> void unbind(@NotNull T object, @NotNull MutableConfigNode node);

    /**
     * Creates a binder builder with custom options.
     *
     * @return a new builder
     */
    static @NotNull Builder builder() {
        return new DefaultBinder.Builder();
    }

    /**
     * Creates a default binder.
     *
     * @return a new binder
     */
    static @NotNull Binder create() {
        return builder().build();
    }

    /**
     * Builder for creating customized Binder instances.
     */
    interface Builder {
        /**
         * Sets the naming strategy.
         *
         * @param strategy the naming strategy
         * @return this builder
         */
        @NotNull Builder namingStrategy(@NotNull NamingStrategy strategy);

        /**
         * Sets the serializer registry.
         *
         * @param registry the registry
         * @return this builder
         */
        @NotNull Builder serializers(@NotNull TypeSerializerRegistry registry);

        /**
         * Sets the validator.
         *
         * @param validator the validator
         * @return this builder
         */
        @NotNull Builder validator(@NotNull Validator validator);

        /**
         * Enables or disables implicit defaults.
         *
         * @param enabled true to enable
         * @return this builder
         */
        @NotNull Builder implicitDefaults(boolean enabled);

        /**
         * Enables or disables constructor binding.
         *
         * @param enabled true to enable
         * @return this builder
         */
        @NotNull Builder useConstructorBinding(boolean enabled);

        /**
         * Builds the binder.
         *
         * @return the binder
         */
        @NotNull Binder build();
    }
}
