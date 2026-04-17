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
package tech.guilhermekaua.spigotboot.versions.api.goal;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a library-managed identifier for a custom goal definition.
 *
 * @since 2.0.2
 */
public final class CustomGoalKey {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9._-]+");

    private final String namespace;
    private final String value;
    private final String canonicalValue;

    private CustomGoalKey(@NotNull String namespace, @NotNull String value) {
        this.namespace = namespace;
        this.value = value;
        this.canonicalValue = namespace + ":" + value;
    }

    /**
     * Creates a custom goal key from a namespace and value pair.
     *
     * @param namespace the namespace component
     * @param value the value component
     * @return the created key
     */
    public static @NotNull CustomGoalKey of(@NotNull String namespace, @NotNull String value) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        validate(namespace, "namespace");
        validate(value, "value");
        return new CustomGoalKey(namespace, value);
    }

    /**
     * Parses a custom goal key from {@code namespace:value} form.
     *
     * @param canonicalValue the canonical value to parse
     * @return the parsed key
     */
    public static @NotNull CustomGoalKey parse(@NotNull String canonicalValue) {
        Objects.requireNonNull(canonicalValue, "canonicalValue cannot be null");
        int delimiterIndex = canonicalValue.indexOf(':');
        if (delimiterIndex <= 0 || delimiterIndex == canonicalValue.length() - 1) {
            throw new IllegalArgumentException(
                    "Custom goal keys must use the form 'namespace:value'."
            );
        }
        return of(
                canonicalValue.substring(0, delimiterIndex),
                canonicalValue.substring(delimiterIndex + 1)
        );
    }

    /**
     * Returns the namespace component.
     *
     * @return the namespace component
     */
    public @NotNull String namespace() {
        return namespace;
    }

    /**
     * Returns the value component.
     *
     * @return the value component
     */
    public @NotNull String value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CustomGoalKey)) {
            return false;
        }
        CustomGoalKey other = (CustomGoalKey) obj;
        return namespace.equals(other.namespace) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, value);
    }

    @Override
    public @NotNull String toString() {
        return canonicalValue;
    }

    private static void validate(@NotNull String value, @NotNull String label) {
        if (!TOKEN_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Custom goal " + label + " '" + value + "' contains unsupported characters."
            );
        }
    }
}
