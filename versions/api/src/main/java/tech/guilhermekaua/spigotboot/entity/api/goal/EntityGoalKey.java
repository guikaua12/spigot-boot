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
package tech.guilhermekaua.spigotboot.entity.api.goal;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Identifies a goal in a version-agnostic way.
 *
 * @since 2.0.2
 */
public final class EntityGoalKey {
    private final String namespace;
    private final String value;

    private EntityGoalKey(String namespace, String value) {
        this.namespace = namespace;
        this.value = value;
    }

    /**
     * Creates a Minecraft goal key.
     *
     * @param value the goal name
     * @return the created key
     * @throws NullPointerException when the value is null
     */
    public static @NotNull EntityGoalKey minecraft(@NotNull String value) {
        return of("minecraft", value);
    }

    /**
     * Creates a custom goal key.
     *
     * @param namespace the goal namespace
     * @param value the goal value
     * @return the created key
     * @throws NullPointerException when any argument is null
     */
    public static @NotNull EntityGoalKey of(@NotNull String namespace, @NotNull String value) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        return new EntityGoalKey(namespace.trim(), value.trim());
    }

    /**
     * Returns the namespace.
     *
     * @return the namespace
     */
    public @NotNull String namespace() {
        return namespace;
    }

    /**
     * Returns the goal value.
     *
     * @return the goal value
     */
    public @NotNull String value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EntityGoalKey)) {
            return false;
        }
        EntityGoalKey other = (EntityGoalKey) obj;
        return namespace.equals(other.namespace) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, value);
    }

    @Override
    public String toString() {
        return namespace + ":" + value;
    }
}
