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
package tech.guilhermekaua.spigotboot.versions.api.goal;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable request to attach or replace one managed vanilla goal entry.
 *
 * @since 2.0.2
 */
public final class VanillaGoalSpec {
    private final GoalSelectorType selectorType;
    private final VanillaGoalKey key;
    private final int priority;

    VanillaGoalSpec(
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key,
            int priority
    ) {
        this.selectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.priority = priority;
    }

    /**
     * Creates a vanilla goal specification.
     *
     * @param selectorType the selector that owns the goal
     * @param key the managed vanilla goal key
     * @param priority the selector priority
     * @return the goal specification
     */
    public static @NotNull VanillaGoalSpec of(
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key,
            int priority
    ) {
        return new VanillaGoalSpec(selectorType, key, priority);
    }

    /**
     * Returns the selector that owns the goal.
     *
     * @return the goal selector
     */
    public @NotNull GoalSelectorType selectorType() {
        return selectorType;
    }

    /**
     * Returns the managed vanilla goal key.
     *
     * @return the managed goal key
     */
    public @NotNull VanillaGoalKey key() {
        return key;
    }

    /**
     * Returns the selector priority.
     *
     * @return the selector priority
     */
    public int priority() {
        return priority;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VanillaGoalSpec)) {
            return false;
        }
        VanillaGoalSpec other = (VanillaGoalSpec) obj;
        return priority == other.priority && selectorType == other.selectorType && key == other.key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(selectorType, key, priority);
    }
}
