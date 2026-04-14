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
package tech.guilhermekaua.spigotboot.entity.runtime.bootstrap;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;

import java.util.Objects;

/**
 * Immutable runtime profile resolved before adapter selection.
 *
 * @since 2.0.2
 */
public final class EntityRuntimeProfile {
    private final MinecraftVersion minecraftVersion;
    private final RuntimeServerFlavor serverFlavor;
    private final boolean trackerStateAvailable;
    private final boolean paperChunkSystemAvailable;
    private final boolean paperMoonriseChunkSystemAvailable;

    /**
     * Creates a new runtime profile.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param serverFlavor the resolved runtime flavor
     * @param trackerStateAvailable whether tracker-state hooks are available
     * @param paperChunkSystemAvailable whether the Paper chunk system is available
     * @param paperMoonriseChunkSystemAvailable whether Moonrise chunk-system hooks are available
     */
    public EntityRuntimeProfile(
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull RuntimeServerFlavor serverFlavor,
            boolean trackerStateAvailable,
            boolean paperChunkSystemAvailable,
            boolean paperMoonriseChunkSystemAvailable
    ) {
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        this.serverFlavor = Objects.requireNonNull(serverFlavor, "serverFlavor cannot be null");
        this.trackerStateAvailable = trackerStateAvailable;
        this.paperChunkSystemAvailable = paperChunkSystemAvailable;
        this.paperMoonriseChunkSystemAvailable = paperMoonriseChunkSystemAvailable;
    }

    /**
     * Returns the resolved Minecraft version.
     *
     * @return the resolved version
     */
    public @NotNull MinecraftVersion minecraftVersion() {
        return minecraftVersion;
    }

    /**
     * Returns the resolved server flavor.
     *
     * @return the server flavor
     */
    public @NotNull RuntimeServerFlavor serverFlavor() {
        return serverFlavor;
    }

    /**
     * Returns whether tracker-state hooks are available.
     *
     * @return {@code true} when tracker-state hooks are available
     */
    public boolean trackerStateAvailable() {
        return trackerStateAvailable;
    }

    /**
     * Returns whether the Paper chunk system is available.
     *
     * @return {@code true} when the Paper chunk system is available
     */
    public boolean paperChunkSystemAvailable() {
        return paperChunkSystemAvailable;
    }

    /**
     * Returns whether Moonrise chunk-system hooks are available.
     *
     * @return {@code true} when Moonrise chunk-system hooks are available
     */
    public boolean paperMoonriseChunkSystemAvailable() {
        return paperMoonriseChunkSystemAvailable;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EntityRuntimeProfile)) {
            return false;
        }
        EntityRuntimeProfile that = (EntityRuntimeProfile) object;
        return trackerStateAvailable == that.trackerStateAvailable
                && paperChunkSystemAvailable == that.paperChunkSystemAvailable
                && paperMoonriseChunkSystemAvailable == that.paperMoonriseChunkSystemAvailable
                && minecraftVersion.equals(that.minecraftVersion)
                && serverFlavor == that.serverFlavor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                minecraftVersion,
                serverFlavor,
                Boolean.valueOf(trackerStateAvailable),
                Boolean.valueOf(paperChunkSystemAvailable),
                Boolean.valueOf(paperMoonriseChunkSystemAvailable)
        );
    }

    @Override
    public @NotNull String toString() {
        return "RuntimeProfile{"
                + "minecraftVersion=" + minecraftVersion
                + ", serverFlavor=" + serverFlavor
                + ", trackerStateAvailable=" + trackerStateAvailable
                + ", paperChunkSystemAvailable=" + paperChunkSystemAvailable
                + ", paperMoonriseChunkSystemAvailable=" + paperMoonriseChunkSystemAvailable
                + '}';
    }
}
