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

import org.jetbrains.annotations.NotNull;

/**
 * Callback surface exposed by the shared legacy tracker-entry backend.
 *
 * @since 2.0.2
 */
public interface LegacyTrackerEntryHook {

    /**
     * Dispatches the legacy tracker tick callback.
     */
    void onTrack();

    /**
     * Dispatches a legacy per-viewer viewability update.
     *
     * @param rawViewer the version-local viewer handle
     */
    void onViewerUpdate(@NotNull Object rawViewer);

    /**
     * Dispatches a legacy per-viewer removal callback.
     *
     * @param rawViewer the version-local viewer handle
     */
    void onViewerRemoved(@NotNull Object rawViewer);

    /**
     * Dispatches a legacy hide-for-all callback.
     */
    void onHideForAll();
}
