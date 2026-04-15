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
package tech.guilhermekaua.spigotboot.versions.runtime.strategy;

import org.jetbrains.annotations.NotNull;

/**
 * Describes the runtime-selected tracking and handle-binding family for spawned and attached entities.
 *
 * @since 2.0.2
 */
public interface TrackingBindingStrategy {

    /**
     * Returns the runtime-selected strategy id.
     *
     * @return the selected strategy id
     */
    @NotNull String id();

    /**
     * Returns whether fresh spawn exposes a tracker-entry handle.
     *
     * @return {@code true} when a fresh-spawn tracker-entry handle is available
     */
    boolean freshSpawnTrackerEntryHandleAvailable();

    /**
     * Returns whether fresh spawn exposes a tracker-state handle.
     *
     * @return {@code true} when a fresh-spawn tracker-state handle is available
     */
    boolean freshSpawnTrackerStateHandleAvailable();

    /**
     * Returns whether replacement exposes a tracker-entry handle.
     *
     * @return {@code true} when a replacement tracker-entry handle is available
     */
    boolean replacementTrackerEntryHandleAvailable();

    /**
     * Returns whether replacement exposes a tracker-state handle.
     *
     * @return {@code true} when a replacement tracker-state handle is available
     */
    boolean replacementTrackerStateHandleAvailable();
}
