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
 * One active potion or status effect prepared for network synchronization.
 *
 * @since 2.0.2
 */
public final class ActiveEffect {
    private final String key;
    private final int amplifier;
    private final int durationTicks;
    private final boolean ambient;
    private final boolean particlesVisible;

    /**
     * Creates one active effect snapshot.
     *
     * @param key the logical effect key
     * @param amplifier the effect amplifier
     * @param durationTicks the effect duration in ticks
     * @param ambient whether the effect is ambient
     * @param particlesVisible whether particles are visible
     */
    public ActiveEffect(
            @NotNull String key,
            int amplifier,
            int durationTicks,
            boolean ambient,
            boolean particlesVisible
    ) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
        this.ambient = ambient;
        this.particlesVisible = particlesVisible;
    }

    /**
     * Returns the logical effect key.
     *
     * @return the effect key
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * Returns the effect amplifier.
     *
     * @return the amplifier
     */
    public int amplifier() {
        return amplifier;
    }

    /**
     * Returns the effect duration in ticks.
     *
     * @return the duration in ticks
     */
    public int durationTicks() {
        return durationTicks;
    }

    /**
     * Returns whether the effect is ambient.
     *
     * @return {@code true} when the effect is ambient
     */
    public boolean ambient() {
        return ambient;
    }

    /**
     * Returns whether particles are visible.
     *
     * @return {@code true} when particles are visible
     */
    public boolean particlesVisible() {
        return particlesVisible;
    }
}
