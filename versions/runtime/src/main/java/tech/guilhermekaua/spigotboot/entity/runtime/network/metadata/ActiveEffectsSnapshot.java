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
 * Snapshot of active effects prepared for network synchronization.
 *
 * @since 2.0.2
 */
public final class ActiveEffectsSnapshot {
    private static final ActiveEffectsSnapshot EMPTY = new ActiveEffectsSnapshot(Collections.<ActiveEffect>emptyList());

    private final List<ActiveEffect> effects;

    /**
     * Creates a new active effects snapshot.
     *
     * @param effects the active effects in the snapshot
     */
    public ActiveEffectsSnapshot(@NotNull List<ActiveEffect> effects) {
        this.effects = MetadataCollectionSupport.immutableCopy(Objects.requireNonNull(effects, "effects cannot be null"));
    }

    /**
     * Returns the shared empty effects snapshot.
     *
     * @return the empty effects snapshot
     */
    public static @NotNull ActiveEffectsSnapshot empty() {
        return EMPTY;
    }

    /**
     * Returns the active effects.
     *
     * @return the active effects
     */
    public @NotNull List<ActiveEffect> effects() {
        return effects;
    }

    /**
     * Returns whether the effects snapshot is empty.
     *
     * @return {@code true} when there are no active effects
     */
    public boolean isEmpty() {
        return effects.isEmpty();
    }
}
