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
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable outcome for one managed goal mutation.
 *
 * <p>Removal results report how many recognized managed entries were removed from one selector. When a
 * supported managed key is absent, the removal count is zero.</p>
 *
 * @since 2.0.2
 */
public final class GoalOperationResult {
    /**
     * The type of mutation that produced this result.
     */
    public enum Operation {
        /**
         * A managed goal was added or replaced.
         */
        ADD,

        /**
         * Managed goal entries were removed by key.
         */
        REMOVE
    }

    private final Operation operation;
    private final GoalSelectorType selectorType;
    private final VanillaGoalKey vanillaKey;
    private final CustomGoalKey customKey;
    private final int removedEntries;
    private final boolean replacedExistingEntry;

    private GoalOperationResult(
            @NotNull Operation operation,
            @NotNull GoalSelectorType selectorType,
            @Nullable VanillaGoalKey vanillaKey,
            @Nullable CustomGoalKey customKey,
            int removedEntries,
            boolean replacedExistingEntry
    ) {
        this.operation = Objects.requireNonNull(operation, "operation cannot be null");
        this.selectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
        if ((vanillaKey == null) == (customKey == null)) {
            throw new IllegalArgumentException("Exactly one managed goal key must be provided.");
        }
        if (removedEntries < 0) {
            throw new IllegalArgumentException("removedEntries cannot be negative.");
        }
        this.vanillaKey = vanillaKey;
        this.customKey = customKey;
        this.removedEntries = removedEntries;
        this.replacedExistingEntry = replacedExistingEntry;
    }

    /**
     * Creates an add result for a vanilla goal key.
     *
     * @param selectorType the selector that owns the goal
     * @param key the managed vanilla goal key
     * @param replacedExistingEntry whether an earlier managed entry was replaced
     * @return the add result
     */
    public static @NotNull GoalOperationResult added(
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key,
            boolean replacedExistingEntry
    ) {
        return new GoalOperationResult(Operation.ADD, selectorType, key, null, 0, replacedExistingEntry);
    }

    /**
     * Creates an add result for a custom goal key.
     *
     * @param selectorType the selector that owns the goal
     * @param key the managed custom goal key
     * @param replacedExistingEntry whether an earlier managed entry was replaced
     * @return the add result
     */
    public static @NotNull GoalOperationResult added(
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key,
            boolean replacedExistingEntry
    ) {
        return new GoalOperationResult(Operation.ADD, selectorType, null, key, 0, replacedExistingEntry);
    }

    /**
     * Creates a removal result for a vanilla goal key.
     *
     * @param selectorType the selector that was mutated
     * @param key the managed vanilla goal key
     * @param removedEntries how many recognized managed entries were removed
     * @return the removal result
     */
    public static @NotNull GoalOperationResult removed(
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key,
            int removedEntries
    ) {
        return new GoalOperationResult(Operation.REMOVE, selectorType, key, null, removedEntries, false);
    }

    /**
     * Creates a removal result for a custom goal key.
     *
     * @param selectorType the selector that was mutated
     * @param key the managed custom goal key
     * @param removedEntries how many recognized managed entries were removed
     * @return the removal result
     */
    public static @NotNull GoalOperationResult removed(
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key,
            int removedEntries
    ) {
        return new GoalOperationResult(Operation.REMOVE, selectorType, null, key, removedEntries, false);
    }

    /**
     * Returns the mutation type.
     *
     * @return the mutation type
     */
    public @NotNull Operation operation() {
        return operation;
    }

    /**
     * Returns the selector that was mutated.
     *
     * @return the selector that was mutated
     */
    public @NotNull GoalSelectorType selectorType() {
        return selectorType;
    }

    /**
     * Returns the managed vanilla goal key, or {@code null} when the mutation targeted a custom key.
     *
     * @return the managed vanilla goal key, or {@code null}
     */
    public @Nullable VanillaGoalKey vanillaKeyOrNull() {
        return vanillaKey;
    }

    /**
     * Returns the managed custom goal key, or {@code null} when the mutation targeted a vanilla key.
     *
     * @return the managed custom goal key, or {@code null}
     */
    public @Nullable CustomGoalKey customKeyOrNull() {
        return customKey;
    }

    /**
     * Returns how many recognized managed entries were removed from the selector.
     *
     * @return how many recognized managed entries were removed from the selector
     */
    public int removedEntries() {
        return removedEntries;
    }

    /**
     * Returns whether an earlier managed entry with the same key was replaced.
     *
     * @return {@code true} when a managed entry was replaced
     */
    public boolean replacedExistingEntry() {
        return replacedExistingEntry;
    }
}
