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

/**
 * Optional head rotation payload prepared for network synchronization.
 *
 * @since 2.0.2
 */
public final class HeadRotation {
    private static final HeadRotation ABSENT = new HeadRotation(false, 0.0F);

    private final boolean present;
    private final float yaw;

    private HeadRotation(boolean present, float yaw) {
        this.present = present;
        this.yaw = yaw;
    }

    /**
     * Creates a head rotation payload.
     *
     * @param yaw the head yaw in degrees
     * @return the head rotation payload
     */
    public static @NotNull HeadRotation of(float yaw) {
        return new HeadRotation(true, yaw);
    }

    /**
     * Returns the shared absent head rotation payload.
     *
     * @return the absent head rotation payload
     */
    public static @NotNull HeadRotation absent() {
        return ABSENT;
    }

    /**
     * Returns whether a head rotation payload is present.
     *
     * @return {@code true} when a head rotation payload is present
     */
    public boolean isPresent() {
        return present;
    }

    /**
     * Returns the head yaw in degrees.
     *
     * @return the head yaw in degrees
     */
    public float yaw() {
        return yaw;
    }
}
