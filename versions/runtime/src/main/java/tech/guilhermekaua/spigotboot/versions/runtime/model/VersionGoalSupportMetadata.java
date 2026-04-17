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
package tech.guilhermekaua.spigotboot.versions.runtime.model;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable runtime-facing summary of optional managed-goal support exposed by one exact-version adapter.
 *
 * @since 2.0.2
 */
public final class VersionGoalSupportMetadata {
    private static final VersionGoalSupportMetadata UNSPECIFIED = new VersionGoalSupportMetadata(
            Collections.<VanillaGoalKey>emptySet(),
            false,
            false,
            false
    );

    private final Set<VanillaGoalKey> supportedVanillaGoalKeys;
    private final boolean attachedManagedSnapshotAvailable;
    private final boolean spawnedExecutorFactoryAvailable;
    private final boolean attachedExecutorFactoryAvailable;

    /**
     * Creates a new immutable goal-support metadata summary.
     *
     * @param supportedVanillaGoalKeys the stable managed vanilla goal keys recognized by the adapter
     * @param attachedManagedSnapshotAvailable whether attached entities can expose a recognized managed snapshot
     * @param spawnedExecutorFactoryAvailable whether spawned entities can receive a version-owned executor seam
     * @param attachedExecutorFactoryAvailable whether attached entities can receive a version-owned executor seam
     */
    public VersionGoalSupportMetadata(
            @NotNull Set<VanillaGoalKey> supportedVanillaGoalKeys,
            boolean attachedManagedSnapshotAvailable,
            boolean spawnedExecutorFactoryAvailable,
            boolean attachedExecutorFactoryAvailable
    ) {
        Objects.requireNonNull(supportedVanillaGoalKeys, "supportedVanillaGoalKeys cannot be null");
        this.supportedVanillaGoalKeys = Collections.unmodifiableSet(EnumSet.copyOf(
                supportedVanillaGoalKeys.isEmpty()
                        ? EnumSet.noneOf(VanillaGoalKey.class)
                        : EnumSet.copyOf(supportedVanillaGoalKeys)
        ));
        this.attachedManagedSnapshotAvailable = attachedManagedSnapshotAvailable;
        this.spawnedExecutorFactoryAvailable = spawnedExecutorFactoryAvailable;
        this.attachedExecutorFactoryAvailable = attachedExecutorFactoryAvailable;
    }

    /**
     * Returns an unspecified metadata summary used when an adapter does not advertise goal support yet.
     *
     * @return the unspecified metadata summary
     */
    public static @NotNull VersionGoalSupportMetadata unspecified() {
        return UNSPECIFIED;
    }

    /**
     * Returns the stable managed vanilla goal keys recognized by the adapter.
     *
     * @return the recognized managed vanilla goal keys
     */
    public @NotNull Set<VanillaGoalKey> supportedVanillaGoalKeys() {
        return supportedVanillaGoalKeys;
    }

    /**
     * Returns whether attached entities can expose a recognized managed snapshot.
     *
     * @return {@code true} when attached snapshot support is available
     */
    public boolean attachedManagedSnapshotAvailable() {
        return attachedManagedSnapshotAvailable;
    }

    /**
     * Returns whether spawned entities can receive a version-owned executor seam.
     *
     * @return {@code true} when spawned executor support is available
     */
    public boolean spawnedExecutorFactoryAvailable() {
        return spawnedExecutorFactoryAvailable;
    }

    /**
     * Returns whether attached entities can receive a version-owned executor seam.
     *
     * @return {@code true} when attached executor support is available
     */
    public boolean attachedExecutorFactoryAvailable() {
        return attachedExecutorFactoryAvailable;
    }

    /**
     * Returns whether this metadata advertises any concrete managed-goal support yet.
     *
     * @return {@code true} when any managed-goal capability is advertised
     */
    public boolean specified() {
        return !supportedVanillaGoalKeys.isEmpty()
                || attachedManagedSnapshotAvailable
                || spawnedExecutorFactoryAvailable
                || attachedExecutorFactoryAvailable;
    }
}
