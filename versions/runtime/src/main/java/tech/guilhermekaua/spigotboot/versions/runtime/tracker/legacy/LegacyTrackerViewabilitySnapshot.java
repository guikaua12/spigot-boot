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

/**
 * Immutable viewability inputs consumed by the shared legacy tracker backend.
 *
 * @since 2.0.2
 */
public final class LegacyTrackerViewabilitySnapshot {
    private static final LegacyTrackerViewabilitySnapshot HIDDEN = new LegacyTrackerViewabilitySnapshot(
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false
    );

    private final boolean selfViewer;
    private final boolean respawnBlind;
    private final boolean seenByEntity;
    private final boolean entityViewable;
    private final boolean passengerViewable;
    private final boolean withinTrackingRange;
    private final boolean chunkVisible;
    private final boolean viewerCanSeeTrackedPlayer;

    public LegacyTrackerViewabilitySnapshot(
            boolean selfViewer,
            boolean respawnBlind,
            boolean seenByEntity,
            boolean entityViewable,
            boolean passengerViewable,
            boolean withinTrackingRange,
            boolean chunkVisible,
            boolean viewerCanSeeTrackedPlayer
    ) {
        this.selfViewer = selfViewer;
        this.respawnBlind = respawnBlind;
        this.seenByEntity = seenByEntity;
        this.entityViewable = entityViewable;
        this.passengerViewable = passengerViewable;
        this.withinTrackingRange = withinTrackingRange;
        this.chunkVisible = chunkVisible;
        this.viewerCanSeeTrackedPlayer = viewerCanSeeTrackedPlayer;
    }

    public static LegacyTrackerViewabilitySnapshot hidden() {
        return HIDDEN;
    }

    public boolean selfViewer() {
        return selfViewer;
    }

    public boolean respawnBlind() {
        return respawnBlind;
    }

    public boolean seenByEntity() {
        return seenByEntity;
    }

    public boolean entityViewable() {
        return entityViewable;
    }

    public boolean passengerViewable() {
        return passengerViewable;
    }

    public boolean withinTrackingRange() {
        return withinTrackingRange;
    }

    public boolean chunkVisible() {
        return chunkVisible;
    }

    public boolean viewerCanSeeTrackedPlayer() {
        return viewerCanSeeTrackedPlayer;
    }
}
