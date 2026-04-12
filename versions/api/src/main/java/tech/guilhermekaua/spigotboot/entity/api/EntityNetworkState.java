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

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mutable network synchronization state associated with one live controlled entity.
 *
 * @since 2.0.2
 */
public final class EntityNetworkState {
    private double liveX;
    private double liveY;
    private double liveZ;
    private double syncedX;
    private double syncedY;
    private double syncedZ;
    private double liveVelocityX;
    private double liveVelocityY;
    private double liveVelocityZ;
    private double syncedVelocityX;
    private double syncedVelocityY;
    private double syncedVelocityZ;
    private float liveYaw;
    private float livePitch;
    private float syncedYaw;
    private float syncedPitch;
    private float liveHeadYaw;
    private float syncedHeadYaw;
    private int ticksSinceAbsoluteSync;
    private Object trackerEntryHandle;
    private Object trackerStateHandle;
    private final Set<Player> viewers = new LinkedHashSet<Player>();

    public double liveX() { return liveX; }
    public double liveY() { return liveY; }
    public double liveZ() { return liveZ; }
    public void setLivePosition(double x, double y, double z) { this.liveX = x; this.liveY = y; this.liveZ = z; }
    public double syncedX() { return syncedX; }
    public double syncedY() { return syncedY; }
    public double syncedZ() { return syncedZ; }
    public void setSyncedPosition(double x, double y, double z) { this.syncedX = x; this.syncedY = y; this.syncedZ = z; }
    public double liveVelocityX() { return liveVelocityX; }
    public double liveVelocityY() { return liveVelocityY; }
    public double liveVelocityZ() { return liveVelocityZ; }
    public void setLiveVelocity(double x, double y, double z) { this.liveVelocityX = x; this.liveVelocityY = y; this.liveVelocityZ = z; }
    public double syncedVelocityX() { return syncedVelocityX; }
    public double syncedVelocityY() { return syncedVelocityY; }
    public double syncedVelocityZ() { return syncedVelocityZ; }
    public void setSyncedVelocity(double x, double y, double z) { this.syncedVelocityX = x; this.syncedVelocityY = y; this.syncedVelocityZ = z; }
    public float liveYaw() { return liveYaw; }
    public float livePitch() { return livePitch; }
    public void setLiveRotation(float yaw, float pitch) { this.liveYaw = yaw; this.livePitch = pitch; }
    public float syncedYaw() { return syncedYaw; }
    public float syncedPitch() { return syncedPitch; }
    public void setSyncedRotation(float yaw, float pitch) { this.syncedYaw = yaw; this.syncedPitch = pitch; }
    public float liveHeadYaw() { return liveHeadYaw; }
    public void setLiveHeadYaw(float liveHeadYaw) { this.liveHeadYaw = liveHeadYaw; }
    public float syncedHeadYaw() { return syncedHeadYaw; }
    public void setSyncedHeadYaw(float syncedHeadYaw) { this.syncedHeadYaw = syncedHeadYaw; }
    public int ticksSinceAbsoluteSync() { return ticksSinceAbsoluteSync; }
    public void setTicksSinceAbsoluteSync(int ticksSinceAbsoluteSync) { this.ticksSinceAbsoluteSync = ticksSinceAbsoluteSync; }
    public void incrementTicksSinceAbsoluteSync() { this.ticksSinceAbsoluteSync++; }
    public void markPositionSyncedFromLive() { setSyncedPosition(liveX, liveY, liveZ); }
    public void markVelocitySyncedFromLive() { setSyncedVelocity(liveVelocityX, liveVelocityY, liveVelocityZ); }
    public void markRotationSyncedFromLive() { setSyncedRotation(liveYaw, livePitch); }
    public void markHeadRotationSyncedFromLive() { setSyncedHeadYaw(liveHeadYaw); }
    public void resetAbsoluteSyncCounter() { this.ticksSinceAbsoluteSync = 0; }
    public @Nullable Object trackerEntryHandle() { return trackerEntryHandle; }
    public void setTrackerEntryHandle(@Nullable Object trackerEntryHandle) { this.trackerEntryHandle = trackerEntryHandle; }
    public @Nullable Object trackerStateHandle() { return trackerStateHandle; }
    public void setTrackerStateHandle(@Nullable Object trackerStateHandle) { this.trackerStateHandle = trackerStateHandle; }
    public @NotNull Set<Player> viewers() { return Collections.unmodifiableSet(viewers); }
    public boolean addViewer(@NotNull Player viewer) { return viewers.add(viewer); }
    public boolean removeViewer(@NotNull Player viewer) { return viewers.remove(viewer); }
    public void clearViewers() { viewers.clear(); }
}
