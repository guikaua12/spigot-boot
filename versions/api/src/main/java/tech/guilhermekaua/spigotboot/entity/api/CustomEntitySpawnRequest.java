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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable version-agnostic request for spawning a custom entity.
 *
 * @since 2.0.2
 */
public final class CustomEntitySpawnRequest {
    private final Location location;
    private final CustomEntityDataView data;

    private CustomEntitySpawnRequest(Location location, CustomEntityDataView data) {
        this.location = location;
        this.data = data;
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
     * Returns the spawn location.
     *
     * @return the spawn location
     */
    public @NotNull Location location() {
        return location.clone();
    }

    /**
     * Returns the immutable data associated with the spawn request.
     *
     * @return the spawn data
     */
    public @NotNull CustomEntityDataView data() {
        return data;
    }

    /**
     * Builds immutable spawn requests.
     */
    public static final class Builder {
        private final Location location;
        private final Map<String, Object> data = new LinkedHashMap<String, Object>();

        private Builder(Location location) {
            this.location = Objects.requireNonNull(location, "location cannot be null").clone();
            if (this.location.getWorld() == null) {
                throw new IllegalArgumentException("location world cannot be null");
            }
        }

        /**
         * Stores arbitrary spawn metadata.
         *
         * @param key the key to store
         * @param value the value to store
         * @return the builder
         */
        public @NotNull Builder put(@NotNull String key, @NotNull Object value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            data.put(key, value);
            return this;
        }

        /**
         * Creates the immutable request.
         *
         * @return the immutable request
         */
        public @NotNull CustomEntitySpawnRequest build() {
            return new CustomEntitySpawnRequest(location.clone(), CustomEntityDataView.of(data));
        }
    }
}
