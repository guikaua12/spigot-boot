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
package tech.guilhermekaua.spigotboot.versions.runtime.selection;

import org.jetbrains.annotations.NotNull;

/**
 * Identifies the runtime packet transport backend family selected for one profile.
 *
 * @since 2.0.2
 */
public enum EntityTransportFamily {

    /**
     * No packet transport family could be inferred.
     */
    UNSPECIFIED("unspecified"),

    /**
     * Shared legacy transport family used by 1.8.8-1.13.2 runtimes.
     */
    LEGACY_1_8_TO_1_13_2("legacy-1_8-to-1_13_2"),

    /**
     * Shared modern transport family used by 1.14-1.16.5 runtimes.
     */
    MODERN_1_14_TO_1_16_5("modern-1_14-to-1_16_5"),

    /**
     * Shared modern transport family used by 1.17-1.18.2 runtimes.
     */
    MODERN_1_17_TO_1_18_2("modern-1_17-to-1_18_2"),

    /**
     * Shared modern transport family used by 1.19.2-1.20.6 runtimes.
     */
    MODERN_1_19_2_TO_1_20_6("modern-1_19_2-to-1_20_6"),

    /**
     * Shared latest transport family used by 1.21.x runtimes.
     */
    LATEST_1_21_X("latest-1_21-x");

    private final String id;

    EntityTransportFamily(@NotNull String id) {
        this.id = id;
    }

    /**
     * Returns the stable runtime id for the selected transport family.
     *
     * @return the transport family id
     */
    public @NotNull String id() {
        return id;
    }
}
