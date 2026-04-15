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
package tech.guilhermekaua.spigotboot.versions.runtime.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Version-specific binding bundle for downstream entity runtime strategies.
 *
 * @since 2.0.2
 */
public final class VersionBindings {
    private static final VersionBindings UNSPECIFIED = new VersionBindings(
            EntityFreshSpawnBinding.unspecified(),
            EntityReplacementBinding.unspecified()
    );

    private final EntityFreshSpawnBinding freshSpawn;
    private final EntityReplacementBinding replacement;

    /**
     * Creates a new version binding bundle.
     *
     * @param freshSpawn the fresh-spawn bindings
     * @param replacement the replacement bindings
     */
    public VersionBindings(
            @NotNull EntityFreshSpawnBinding freshSpawn,
            @NotNull EntityReplacementBinding replacement
    ) {
        this.freshSpawn = Objects.requireNonNull(freshSpawn, "freshSpawn cannot be null");
        this.replacement = Objects.requireNonNull(replacement, "replacement cannot be null");
    }

    /**
     * Returns the unspecified binding bundle used when an adapter does not expose runtime metadata.
     *
     * @return the unspecified binding bundle
     */
    public static @NotNull VersionBindings unspecified() {
        return UNSPECIFIED;
    }

    /**
     * Returns the fresh-spawn binding details.
     *
     * @return the fresh-spawn bindings
     */
    public @NotNull EntityFreshSpawnBinding freshSpawn() {
        return freshSpawn;
    }

    /**
     * Returns the replacement binding details.
     *
     * @return the replacement bindings
     */
    public @NotNull EntityReplacementBinding replacement() {
        return replacement;
    }
}
