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
package tech.guilhermekaua.spigotboot.versions.runtime.strategy;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable composition root for the runtime-selected entity strategy family.
 *
 * @since 2.0.2
 */
public final class EntityStrategyBundle {
    private final FreshSpawnStrategy freshSpawn;
    private final ReplacementStrategy replacement;
    private final WorldAddStrategy worldAdd;
    private final TrackingBindingStrategy trackingBinding;

    /**
     * Creates a new composed strategy bundle.
     *
     * @param freshSpawn the fresh-spawn strategy
     * @param replacement the replacement strategy
     * @param worldAdd the world-add strategy
     * @param trackingBinding the tracking-binding strategy
     */
    public EntityStrategyBundle(
            @NotNull FreshSpawnStrategy freshSpawn,
            @NotNull ReplacementStrategy replacement,
            @NotNull WorldAddStrategy worldAdd,
            @NotNull TrackingBindingStrategy trackingBinding
    ) {
        this.freshSpawn = Objects.requireNonNull(freshSpawn, "freshSpawn cannot be null");
        this.replacement = Objects.requireNonNull(replacement, "replacement cannot be null");
        this.worldAdd = Objects.requireNonNull(worldAdd, "worldAdd cannot be null");
        this.trackingBinding = Objects.requireNonNull(trackingBinding, "trackingBinding cannot be null");
    }

    /**
     * Returns the selected fresh-spawn strategy.
     *
     * @return the selected fresh-spawn strategy
     */
    public @NotNull FreshSpawnStrategy freshSpawn() {
        return freshSpawn;
    }

    /**
     * Returns the selected replacement strategy.
     *
     * @return the selected replacement strategy
     */
    public @NotNull ReplacementStrategy replacement() {
        return replacement;
    }

    /**
     * Returns the selected world-add strategy.
     *
     * @return the selected world-add strategy
     */
    public @NotNull WorldAddStrategy worldAdd() {
        return worldAdd;
    }

    /**
     * Returns the selected tracking-binding strategy.
     *
     * @return the selected tracking-binding strategy
     */
    public @NotNull TrackingBindingStrategy trackingBinding() {
        return trackingBinding;
    }
}
