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
 * Identifies the runtime managed-goal support family selected for one adapter.
 *
 * @since 2.0.2
 */
public enum EntityGoalSupportFamily {

    /**
     * No managed-goal support family could be inferred.
     */
    UNSPECIFIED("unspecified"),

    /**
     * Shared managed-goal support family used by 1.8.8-1.12.2 runtimes.
     */
    LEGACY_1_8_TO_1_12("legacy-1_8-to-1_12"),

    /**
     * Transitional managed-goal support family used by 1.13-1.13.2 runtimes.
     */
    TRANSITIONAL_1_13("transitional-1_13"),

    /**
     * Shared modern managed-goal support family used by 1.14-1.20.6 runtimes.
     */
    MODERN_1_14_TO_1_20_6("modern-1_14-to-1_20_6"),

    /**
     * Shared latest managed-goal support family used by 1.21.x runtimes.
     */
    LATEST_1_21_X("latest-1_21-x");

    private final String id;

    EntityGoalSupportFamily(@NotNull String id) {
        this.id = id;
    }

    /**
     * Returns the stable runtime id for the selected managed-goal support family.
     *
     * @return the managed-goal support family id
     */
    public @NotNull String id() {
        return id;
    }
}
