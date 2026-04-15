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
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;

import java.util.Objects;

/**
 * Version-specific binding details for replacing or re-binding an existing native entity handle.
 *
 * @since 2.0.2
 */
public final class EntityReplacementBinding {
    private static final EntityReplacementBinding UNSPECIFIED = new EntityReplacementBinding(
            EntityWorldRegistrationMode.UNSPECIFIED,
            false,
            false
    );

    private final EntityWorldRegistrationMode worldRegistrationMode;
    private final boolean trackerEntryHandleAvailable;
    private final boolean trackerStateHandleAvailable;

    /**
     * Creates a new replacement binding description.
     *
     * @param worldRegistrationMode the world-registration mode
     * @param trackerEntryHandleAvailable whether tracker-entry handles are available after replacement
     * @param trackerStateHandleAvailable whether tracker-state handles are available after replacement
     */
    public EntityReplacementBinding(
            @NotNull EntityWorldRegistrationMode worldRegistrationMode,
            boolean trackerEntryHandleAvailable,
            boolean trackerStateHandleAvailable
    ) {
        this.worldRegistrationMode = Objects.requireNonNull(
                worldRegistrationMode,
                "worldRegistrationMode cannot be null"
        );
        this.trackerEntryHandleAvailable = trackerEntryHandleAvailable;
        this.trackerStateHandleAvailable = trackerStateHandleAvailable;
    }

    /**
     * Returns the unspecified replacement binding used when an adapter does not expose runtime metadata.
     *
     * @return the unspecified replacement binding
     */
    public static @NotNull EntityReplacementBinding unspecified() {
        return UNSPECIFIED;
    }

    /**
     * Returns the world-registration mode used while replacing an existing native handle.
     *
     * @return the world-registration mode
     */
    public @NotNull EntityWorldRegistrationMode worldRegistrationMode() {
        return worldRegistrationMode;
    }

    /**
     * Returns whether tracker-entry handles are available after replacement.
     *
     * @return {@code true} when tracker-entry handles are available
     */
    public boolean trackerEntryHandleAvailable() {
        return trackerEntryHandleAvailable;
    }

    /**
     * Returns whether tracker-state handles are available after replacement.
     *
     * @return {@code true} when tracker-state handles are available
     */
    public boolean trackerStateHandleAvailable() {
        return trackerStateHandleAvailable;
    }
}
