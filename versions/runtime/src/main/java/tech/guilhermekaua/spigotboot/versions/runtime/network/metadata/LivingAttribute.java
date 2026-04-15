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
package tech.guilhermekaua.spigotboot.versions.runtime.network.metadata;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * One living-entity attribute value prepared for network synchronization.
 *
 * @since 2.0.2
 */
public final class LivingAttribute {
    private final String key;
    private final double value;

    /**
     * Creates one living attribute value.
     *
     * @param key the logical attribute key
     * @param value the attribute value
     */
    public LivingAttribute(@NotNull String key, double value) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.value = value;
    }

    /**
     * Returns the logical attribute key.
     *
     * @return the attribute key
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * Returns the attribute value.
     *
     * @return the attribute value
     */
    public double value() {
        return value;
    }
}
