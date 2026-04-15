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
package tech.guilhermekaua.spigotboot.versions.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Separate controller surface responsible for tracker and network synchronization behavior.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public abstract class EntityNetworkController<T extends Entity> {
    public static final double RELATIVE_POSITION_THRESHOLD = 128.0D / 4096.0D;
    public static final double MAX_RELATIVE_DISTANCE = 32768.0D / 4096.0D;
    public static final float RELATIVE_ROTATION_THRESHOLD = 4.0F;
    public static final double VELOCITY_THRESHOLD = 0.02D;
    public static final int ABSOLUTE_RESYNC_INTERVAL = 400;

    private static final EntityNetworkController<?> PASS_THROUGH = new EntityNetworkController<Entity>() {
    };

    public void onBind(@NotNull ControlledEntity<T> entity, @NotNull EntityNetworkState state) {
    }

    public void onUnbind(@NotNull ControlledEntity<T> entity, @NotNull EntityNetworkState state) {
    }

    public void onTick(@NotNull ControlledEntity<T> entity, @NotNull EntityNetworkState state) {
    }

    public void onViewerAdded(@NotNull ControlledEntity<T> entity, @NotNull Player viewer, @NotNull EntityNetworkState state) {
    }

    public void onViewerRemoved(@NotNull ControlledEntity<T> entity, @NotNull Player viewer, @NotNull EntityNetworkState state) {
    }

    public boolean shouldSendAbsoluteSync(@NotNull EntityNetworkState state) {
        double deltaX = Math.abs(state.liveX() - state.syncedX());
        double deltaY = Math.abs(state.liveY() - state.syncedY());
        double deltaZ = Math.abs(state.liveZ() - state.syncedZ());
        return deltaX > MAX_RELATIVE_DISTANCE
                || deltaY > MAX_RELATIVE_DISTANCE
                || deltaZ > MAX_RELATIVE_DISTANCE
                || state.ticksSinceAbsoluteSync() >= ABSOLUTE_RESYNC_INTERVAL;
    }

    public boolean shouldSendRelativeSync(@NotNull EntityNetworkState state) {
        if (shouldSendAbsoluteSync(state)) {
            return false;
        }
        double deltaX = Math.abs(state.liveX() - state.syncedX());
        double deltaY = Math.abs(state.liveY() - state.syncedY());
        double deltaZ = Math.abs(state.liveZ() - state.syncedZ());
        return deltaX >= RELATIVE_POSITION_THRESHOLD
                || deltaY >= RELATIVE_POSITION_THRESHOLD
                || deltaZ >= RELATIVE_POSITION_THRESHOLD;
    }

    public boolean shouldSendRotationSync(@NotNull EntityNetworkState state) {
        return Math.abs(state.liveYaw() - state.syncedYaw()) >= RELATIVE_ROTATION_THRESHOLD
                || Math.abs(state.livePitch() - state.syncedPitch()) >= RELATIVE_ROTATION_THRESHOLD
                || Math.abs(state.liveHeadYaw() - state.syncedHeadYaw()) >= RELATIVE_ROTATION_THRESHOLD;
    }

    public boolean shouldSendVelocitySync(@NotNull EntityNetworkState state) {
        return Math.abs(state.liveVelocityX() - state.syncedVelocityX()) >= VELOCITY_THRESHOLD
                || Math.abs(state.liveVelocityY() - state.syncedVelocityY()) >= VELOCITY_THRESHOLD
                || Math.abs(state.liveVelocityZ() - state.syncedVelocityZ()) >= VELOCITY_THRESHOLD;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> @NotNull EntityNetworkController<T> passThrough() {
        return (EntityNetworkController<T>) PASS_THROUGH;
    }
}
