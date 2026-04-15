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
 * Identifies the runtime tracker-hook backend family selected for one profile.
 *
 * @since 2.0.2
 */
public enum EntityTrackerHookFamily {

    /**
     * No runtime tracker-hook family could be inferred.
     */
    UNSPECIFIED("unspecified"),

    /**
     * Shared legacy entry-hook family used by 1.8.8-1.13.2 runtimes.
     */
    LEGACY_ENTRY_HOOK("legacy-entry-hook"),

    /**
     * Shared modern entry-and-state tracker-hook family used by 1.14+ runtimes.
     */
    MODERN_ENTRY_AND_STATE("modern-entry-and-state");

    private final String id;

    EntityTrackerHookFamily(@NotNull String id) {
        this.id = id;
    }

    /**
     * Returns the stable runtime id for the selected tracker-hook family.
     *
     * @return the tracker-hook family id
     */
    public @NotNull String id() {
        return id;
    }
}
