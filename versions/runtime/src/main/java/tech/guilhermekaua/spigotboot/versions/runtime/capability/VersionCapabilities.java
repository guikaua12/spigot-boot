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
package tech.guilhermekaua.spigotboot.versions.runtime.capability;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Small runtime-facing capability summary for one resolved version adapter.
 *
 * @since 2.0.2
 */
public final class VersionCapabilities {
    private static final VersionCapabilities UNSPECIFIED = new VersionCapabilities(
            EntityFreshSpawnPath.UNSPECIFIED,
            false,
            EntityWorldRegistrationMode.UNSPECIFIED,
            EntityWorldRegistrationMode.UNSPECIFIED
    );

    private final EntityFreshSpawnPath freshSpawnPath;
    private final boolean trackerStateHandleAvailable;
    private final EntityWorldRegistrationMode freshSpawnWorldRegistrationMode;
    private final EntityWorldRegistrationMode replacementWorldRegistrationMode;

    /**
     * Creates a new capability summary.
     *
     * @param freshSpawnPath the high-level fresh-spawn path
     * @param trackerStateHandleAvailable whether tracker-state handles are available
     * @param freshSpawnWorldRegistrationMode the fresh-spawn world-registration mode
     * @param replacementWorldRegistrationMode the replacement world-registration mode
     */
    public VersionCapabilities(
            @NotNull EntityFreshSpawnPath freshSpawnPath,
            boolean trackerStateHandleAvailable,
            @NotNull EntityWorldRegistrationMode freshSpawnWorldRegistrationMode,
            @NotNull EntityWorldRegistrationMode replacementWorldRegistrationMode
    ) {
        this.freshSpawnPath = Objects.requireNonNull(freshSpawnPath, "freshSpawnPath cannot be null");
        this.trackerStateHandleAvailable = trackerStateHandleAvailable;
        this.freshSpawnWorldRegistrationMode = Objects.requireNonNull(
                freshSpawnWorldRegistrationMode,
                "freshSpawnWorldRegistrationMode cannot be null"
        );
        this.replacementWorldRegistrationMode = Objects.requireNonNull(
                replacementWorldRegistrationMode,
                "replacementWorldRegistrationMode cannot be null"
        );
    }

    /**
     * Returns the unspecified capability summary used when an adapter does not expose runtime metadata.
     *
     * @return the unspecified summary
     */
    public static @NotNull VersionCapabilities unspecified() {
        return UNSPECIFIED;
    }

    /**
     * Returns the high-level fresh-spawn path.
     *
     * @return the fresh-spawn path
     */
    public @NotNull EntityFreshSpawnPath freshSpawnPath() {
        return freshSpawnPath;
    }

    /**
     * Returns whether the runtime exposes a dedicated tracker-state handle.
     *
     * @return {@code true} when tracker-state handles are available
     */
    public boolean trackerStateHandleAvailable() {
        return trackerStateHandleAvailable;
    }

    /**
     * Returns the world-registration mode used for freshly spawned entities.
     *
     * @return the fresh-spawn world-registration mode
     */
    public @NotNull EntityWorldRegistrationMode freshSpawnWorldRegistrationMode() {
        return freshSpawnWorldRegistrationMode;
    }

    /**
     * Returns the world-registration mode used when replacing or re-binding an existing native handle.
     *
     * @return the replacement world-registration mode
     */
    public @NotNull EntityWorldRegistrationMode replacementWorldRegistrationMode() {
        return replacementWorldRegistrationMode;
    }
}
