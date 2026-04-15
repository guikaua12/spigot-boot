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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Dirty watcher delta used for incremental metadata synchronization.
 *
 * @since 2.0.2
 */
public final class WatcherDelta {
    private static final WatcherDelta EMPTY = new WatcherDelta(Collections.<WatcherItem>emptyList());

    private final List<WatcherItem> items;

    /**
     * Creates a new dirty watcher delta.
     *
     * @param items the dirty watcher items
     */
    public WatcherDelta(@NotNull List<WatcherItem> items) {
        this.items = MetadataCollectionSupport.immutableCopy(Objects.requireNonNull(items, "items cannot be null"));
    }

    /**
     * Returns the shared empty watcher delta.
     *
     * @return the empty watcher delta
     */
    public static @NotNull WatcherDelta empty() {
        return EMPTY;
    }

    /**
     * Returns the dirty watcher items.
     *
     * @return the dirty watcher items
     */
    public @NotNull List<WatcherItem> items() {
        return items;
    }

    /**
     * Returns whether the dirty delta is empty.
     *
     * @return {@code true} when there are no dirty watcher items
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
