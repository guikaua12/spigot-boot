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
package tech.guilhermekaua.spigotboot.entity.api.type;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Identifies a logical entity type exposed by the library.
 *
 * @since 2.0.2
 */
public final class EntityTypeKey {
    private final String namespace;
    private final String value;

    private EntityTypeKey(String namespace, String value) {
        this.namespace = namespace;
        this.value = value;
    }

    /**
     * Creates a Minecraft entity type key.
     *
     * @param value the entity type value
     * @return the created key
     */
    public static @NotNull EntityTypeKey minecraft(@NotNull String value) {
        return of("minecraft", value);
    }

    /**
     * Creates a custom entity type key.
     *
     * @param namespace the namespace
     * @param value the value
     * @return the created key
     */
    public static @NotNull EntityTypeKey of(@NotNull String namespace, @NotNull String value) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        return new EntityTypeKey(namespace.trim(), value.trim());
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
     * Returns the entity type value.
     *
     * @return the type value
     */
    public @NotNull String value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EntityTypeKey)) {
            return false;
        }
        EntityTypeKey other = (EntityTypeKey) obj;
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
