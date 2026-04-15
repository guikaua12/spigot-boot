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

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared entry-hook backend for legacy 1.8.8-1.13.2 tracker families.
 *
 * @since 2.0.2
 */
public final class LegacyTrackerHookBackend implements LegacyTrackerEntryHook {
    private final AbstractRuntimeControlledEntity<?> controlledEntity;
    private final Object trackerEntryHandle;
    private final LegacyTrackerHookSupport support;

    private LegacyTrackerHookBackend(
            @NotNull AbstractRuntimeControlledEntity<?> controlledEntity,
            @NotNull Object trackerEntryHandle,
            @NotNull LegacyTrackerHookSupport support
    ) {
        this.controlledEntity = Objects.requireNonNull(controlledEntity, "controlledEntity cannot be null");
        this.trackerEntryHandle = Objects.requireNonNull(trackerEntryHandle, "trackerEntryHandle cannot be null");
        this.support = Objects.requireNonNull(support, "support cannot be null");
    }

    /**
     * Binds the shared backend onto one legacy tracker-entry handle.
     *
     * @param controlledEntity the runtime-controlled entity
     * @param trackerEntryHandle the tracker-entry handle
     * @param support the version-local bridge
     */
    public static void bind(
            @NotNull AbstractRuntimeControlledEntity<?> controlledEntity,
            @NotNull Object trackerEntryHandle,
            @NotNull LegacyTrackerHookSupport support
    ) {
        Objects.requireNonNull(controlledEntity, "controlledEntity cannot be null");
        Objects.requireNonNull(trackerEntryHandle, "trackerEntryHandle cannot be null");
        Objects.requireNonNull(support, "support cannot be null");

        controlledEntity.bindTrackerHookNetworkDispatch();
        support.installHook(trackerEntryHandle, new LegacyTrackerHookBackend(controlledEntity, trackerEntryHandle, support));
    }

    @Override
    public void onTrack() {
        controlledEntity.dispatchTrackerTick();
    }

    @Override
    public void onViewerUpdate(@NotNull Object rawViewer) {
        Objects.requireNonNull(rawViewer, "rawViewer cannot be null");

        Player viewer = support.resolveViewer(rawViewer);
        if (viewer == null) {
            return;
        }

        LegacyTrackerViewabilitySnapshot snapshot = support.describeViewability(trackerEntryHandle, rawViewer);
        if (shouldTrackViewer(snapshot)) {
            controlledEntity.registerViewer(viewer);
            return;
        }

        controlledEntity.unregisterViewer(viewer);
    }

    @Override
    public void onViewerRemoved(@NotNull Object rawViewer) {
        Objects.requireNonNull(rawViewer, "rawViewer cannot be null");

        Player viewer = support.resolveViewer(rawViewer);
        if (viewer != null) {
            controlledEntity.unregisterViewer(viewer);
        }
    }

    @Override
    public void onHideForAll() {
        List<Player> viewers = new ArrayList<Player>(controlledEntity.networkState().viewers());
        for (Player viewer : viewers) {
            controlledEntity.unregisterViewer(viewer);
        }
    }

    static boolean shouldTrackViewer(@NotNull LegacyTrackerViewabilitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        if (snapshot.selfViewer() || snapshot.respawnBlind() || !snapshot.seenByEntity()) {
            return false;
        }
        if (!snapshot.entityViewable() && !snapshot.passengerViewable()) {
            return false;
        }
        if (!snapshot.withinTrackingRange() || !snapshot.chunkVisible()) {
            return false;
        }
        return snapshot.viewerCanSeeTrackedPlayer();
    }
}
