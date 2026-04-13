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
package tech.guilhermekaua.spigotboot.entity.runtime.model;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Version-specific binding details for fresh native entity spawn.
 *
 * @since 2.0.2
 */
public final class EntityFreshSpawnBinding {
    private static final EntityFreshSpawnBinding UNSPECIFIED = new EntityFreshSpawnBinding(
            Collections.<NativeEntityConstructorShape>emptyList(),
            EntityWorldRegistrationMode.UNSPECIFIED,
            false,
            false
    );

    private final List<NativeEntityConstructorShape> constructorPriority;
    private final EntityWorldRegistrationMode worldRegistrationMode;
    private final boolean trackerEntryHandleAvailable;
    private final boolean trackerStateHandleAvailable;

    /**
     * Creates a new fresh-spawn binding description.
     *
     * @param constructorPriority the supported constructor shapes in resolution order
     * @param worldRegistrationMode the world-registration mode
     * @param trackerEntryHandleAvailable whether tracker-entry handles are available after fresh spawn
     * @param trackerStateHandleAvailable whether tracker-state handles are available after fresh spawn
     */
    public EntityFreshSpawnBinding(
            @NotNull List<NativeEntityConstructorShape> constructorPriority,
            @NotNull EntityWorldRegistrationMode worldRegistrationMode,
            boolean trackerEntryHandleAvailable,
            boolean trackerStateHandleAvailable
    ) {
        Objects.requireNonNull(constructorPriority, "constructorPriority cannot be null");
        this.constructorPriority = Collections.unmodifiableList(
                new ArrayList<NativeEntityConstructorShape>(constructorPriority)
        );
        this.worldRegistrationMode = Objects.requireNonNull(
                worldRegistrationMode,
                "worldRegistrationMode cannot be null"
        );
        this.trackerEntryHandleAvailable = trackerEntryHandleAvailable;
        this.trackerStateHandleAvailable = trackerStateHandleAvailable;
    }

    /**
     * Returns the unspecified fresh-spawn binding used when an adapter does not expose runtime metadata.
     *
     * @return the unspecified fresh-spawn binding
     */
    public static @NotNull EntityFreshSpawnBinding unspecified() {
        return UNSPECIFIED;
    }

    /**
     * Returns the supported constructor shapes in resolution order.
     *
     * @return the constructor resolution order
     */
    public @NotNull List<NativeEntityConstructorShape> constructorPriority() {
        return constructorPriority;
    }

    /**
     * Returns whether the binding exposes any constructor-first spawn shapes.
     *
     * @return {@code true} when at least one constructor shape is available
     */
    public boolean hasConstructorBindings() {
        return !constructorPriority.isEmpty();
    }

    /**
     * Returns the world-registration mode used after fresh native construction.
     *
     * @return the world-registration mode
     */
    public @NotNull EntityWorldRegistrationMode worldRegistrationMode() {
        return worldRegistrationMode;
    }

    /**
     * Returns whether tracker-entry handles are available after fresh spawn.
     *
     * @return {@code true} when tracker-entry handles are available
     */
    public boolean trackerEntryHandleAvailable() {
        return trackerEntryHandleAvailable;
    }

    /**
     * Returns whether tracker-state handles are available after fresh spawn.
     *
     * @return {@code true} when tracker-state handles are available
     */
    public boolean trackerStateHandleAvailable() {
        return trackerStateHandleAvailable;
    }
}
