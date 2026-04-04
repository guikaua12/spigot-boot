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
package tech.guilhermekaua.spigotboot.entity.api;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a version-agnostic spawn request for a custom entity.
 *
 * @since 2.0.2
 */
public final class EntitySpawnRequest {
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    private EntitySpawnRequest(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Creates a spawn request.
     *
     * @param worldName the target world name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param yaw the yaw
     * @param pitch the pitch
     * @return the created request
     * @throws NullPointerException when the world name is null
     */
    public static @NotNull EntitySpawnRequest of(
            @NotNull String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        Objects.requireNonNull(worldName, "worldName cannot be null");
        return new EntitySpawnRequest(worldName, x, y, z, yaw, pitch);
    }

    /**
     * Returns the world name.
     *
     * @return the world name
     */
    public @NotNull String worldName() {
        return worldName;
    }

    /**
     * Returns the x coordinate.
     *
     * @return the x coordinate
     */
    public double x() {
        return x;
    }

    /**
     * Returns the y coordinate.
     *
     * @return the y coordinate
     */
    public double y() {
        return y;
    }

    /**
     * Returns the z coordinate.
     *
     * @return the z coordinate
     */
    public double z() {
        return z;
    }

    /**
     * Returns the yaw.
     *
     * @return the yaw
     */
    public float yaw() {
        return yaw;
    }

    /**
     * Returns the pitch.
     *
     * @return the pitch
     */
    public float pitch() {
        return pitch;
    }
}
