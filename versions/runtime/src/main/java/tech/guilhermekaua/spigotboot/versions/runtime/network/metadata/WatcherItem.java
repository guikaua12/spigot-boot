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
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One logical watcher item in a runtime-owned metadata payload.
 *
 * @since 2.0.2
 */
public final class WatcherItem {
    private final int index;
    private final String typeId;
    private final Object value;

    /**
     * Creates one watcher item.
     *
     * @param index the logical watcher index
     * @param typeId the logical watcher type identifier
     * @param value the watcher value
     */
    public WatcherItem(int index, @NotNull String typeId, @Nullable Object value) {
        this.index = index;
        this.typeId = Objects.requireNonNull(typeId, "typeId cannot be null");
        this.value = value;
    }

    /**
     * Returns the logical watcher index.
     *
     * @return the watcher index
     */
    public int index() {
        return index;
    }

    /**
     * Returns the logical watcher type identifier.
     *
     * @return the watcher type identifier
     */
    public @NotNull String typeId() {
        return typeId;
    }

    /**
     * Returns the watcher value.
     *
     * @return the watcher value
     */
    public @Nullable Object value() {
        return value;
    }
}
