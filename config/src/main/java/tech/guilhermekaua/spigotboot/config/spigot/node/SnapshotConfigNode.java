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
package tech.guilhermekaua.spigotboot.config.spigot.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.node.AbstractValueConfigNode;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

/**
 * Detached immutable {@link ConfigNode} backed by plain map/list/scalar values.
 * <p>
 * Used when resolution needs to rebuild part of a node tree without mutating
 * the original implementation-specific node instance.
 */
public final class SnapshotConfigNode extends AbstractValueConfigNode {

    private final Object value;
    private final PropertyPath path;
    private final boolean virtual;

    private SnapshotConfigNode(@Nullable Object value, @NotNull PropertyPath path, boolean virtual) {
        this.value = value;
        this.path = path;
        this.virtual = virtual;
    }

    public static @NotNull SnapshotConfigNode of(@Nullable Object value, @NotNull PropertyPath path) {
        return new SnapshotConfigNode(value, path, false);
    }

    public static @NotNull SnapshotConfigNode virtual(@NotNull PropertyPath path) {
        return new SnapshotConfigNode(null, path, true);
    }

    @Override
    protected @Nullable Object currentValue() {
        return value;
    }

    @Override
    protected @NotNull PropertyPath currentPath() {
        return path;
    }

    @Override
    protected boolean currentVirtual() {
        return virtual;
    }

    @Override
    protected @NotNull ConfigNode createChildNode(
            @Nullable Object value,
            @NotNull PropertyPath path,
            boolean virtual) {
        return new SnapshotConfigNode(value, path, virtual);
    }
}
