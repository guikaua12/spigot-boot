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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Legacy spawn request wrapper kept for migration from the old custom-entity API.
 *
 * @since 2.0.2
 * @deprecated use {@link SpawnOptions}
 */
@Deprecated
public final class CustomEntitySpawnRequest {
    private final SpawnOptions spawnOptions;

    private CustomEntitySpawnRequest(@NotNull SpawnOptions spawnOptions) {
        this.spawnOptions = Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
    }

    /**
     * Creates a new builder for the supplied location.
     *
     * @param location the spawn location
     * @return the builder
     */
    public static @NotNull Builder builder(@NotNull Location location) {
        return new Builder(location);
    }

    /**
     * Creates a legacy wrapper for the supplied spawn options.
     *
     * @param spawnOptions the spawn options
     * @return the wrapped request
     */
    public static @NotNull CustomEntitySpawnRequest fromOptions(@NotNull SpawnOptions spawnOptions) {
        return new CustomEntitySpawnRequest(spawnOptions);
    }

    /**
     * Returns the spawn location.
     *
     * @return the spawn location
     */
    public @NotNull Location location() {
        return spawnOptions.location();
    }

    /**
     * Returns the immutable data associated with the spawn request.
     *
     * @return the spawn data
     */
    public @NotNull CustomEntityDataView data() {
        return spawnOptions.data();
    }

    /**
     * Returns the new spawn-options view for this legacy request.
     *
     * @return the spawn options
     */
    public @NotNull SpawnOptions toSpawnOptions() {
        return spawnOptions;
    }

    /**
     * Builds immutable spawn requests.
     */
    public static final class Builder {
        private final SpawnOptions.Builder delegate;

        private Builder(Location location) {
            this.delegate = SpawnOptions.builder(Objects.requireNonNull(location, "location cannot be null"));
        }

        /**
         * Stores arbitrary spawn metadata.
         *
         * @param key the key to store
         * @param value the value to store
         * @return the builder
         */
        public @NotNull Builder put(@NotNull String key, @NotNull Object value) {
            delegate.data(key, value);
            return this;
        }

        /**
         * Creates the immutable request.
         *
         * @return the immutable request
         */
        public @NotNull CustomEntitySpawnRequest build() {
            return new CustomEntitySpawnRequest(delegate.build());
        }
    }
}
