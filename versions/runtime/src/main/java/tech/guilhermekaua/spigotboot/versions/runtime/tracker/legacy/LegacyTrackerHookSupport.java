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
package tech.guilhermekaua.spigotboot.versions.runtime.tracker.legacy;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Version-local bridge used by the shared legacy tracker-entry backend.
 *
 * @since 2.0.2
 */
public interface LegacyTrackerHookSupport {

    /**
     * Returns the version-local overlay id for this legacy hook bridge.
     *
     * @return the overlay id
     */
    @NotNull String overlayId();

    /**
     * Installs the shared legacy hook onto one tracker-entry handle.
     *
     * @param trackerEntryHandle the version-local tracker-entry handle
     * @param hook the shared runtime hook
     */
    void installHook(@NotNull Object trackerEntryHandle, @NotNull LegacyTrackerEntryHook hook);

    /**
     * Resolves a Bukkit player wrapper from the version-local viewer handle.
     *
     * @param rawViewer the version-local viewer handle
     * @return the Bukkit viewer wrapper, or {@code null}
     */
    @Nullable Player resolveViewer(@NotNull Object rawViewer);

    /**
     * Describes the legacy viewability inputs for one viewer candidate.
     *
     * @param trackerEntryHandle the version-local tracker-entry handle
     * @param rawViewer the version-local viewer handle
     * @return the extracted viewability snapshot
     */
    @NotNull LegacyTrackerViewabilitySnapshot describeViewability(
            @NotNull Object trackerEntryHandle,
            @NotNull Object rawViewer
    );
}
