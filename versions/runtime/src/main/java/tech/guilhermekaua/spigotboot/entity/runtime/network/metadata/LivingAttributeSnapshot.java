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
package tech.guilhermekaua.spigotboot.entity.runtime.network.metadata;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of living-entity attributes for one metadata synchronization pass.
 *
 * @since 2.0.2
 */
public final class LivingAttributeSnapshot {
    private static final LivingAttributeSnapshot EMPTY = new LivingAttributeSnapshot(
            Collections.<LivingAttribute>emptyList()
    );

    private final List<LivingAttribute> attributes;

    /**
     * Creates a new living attribute snapshot.
     *
     * @param attributes the attribute values in the snapshot
     */
    public LivingAttributeSnapshot(@NotNull List<LivingAttribute> attributes) {
        this.attributes = MetadataCollectionSupport.immutableCopy(
                Objects.requireNonNull(attributes, "attributes cannot be null")
        );
    }

    /**
     * Returns the shared empty attribute snapshot.
     *
     * @return the empty attribute snapshot
     */
    public static @NotNull LivingAttributeSnapshot empty() {
        return EMPTY;
    }

    /**
     * Returns the living attributes.
     *
     * @return the living attributes
     */
    public @NotNull List<LivingAttribute> attributes() {
        return attributes;
    }

    /**
     * Returns whether the attribute snapshot is empty.
     *
     * @return {@code true} when there are no attributes
     */
    public boolean isEmpty() {
        return attributes.isEmpty();
    }
}
