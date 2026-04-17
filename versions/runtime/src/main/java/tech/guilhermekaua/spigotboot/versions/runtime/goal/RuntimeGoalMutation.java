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
package tech.guilhermekaua.spigotboot.versions.runtime.goal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalSpec;

import java.util.Objects;

/**
 * Immutable description of one queued managed-goal mutation.
 *
 * @since 2.0.2
 */
public final class RuntimeGoalMutation {
    /**
     * The queued mutation kind.
     */
    public enum Operation {
        ADD_VANILLA,
        REMOVE_VANILLA,
        ADD_CUSTOM,
        REMOVE_CUSTOM,
        CLEAR_SELECTOR
    }

    private final Operation operation;
    private final GoalSelectorType selectorType;
    private final VanillaGoalSpec vanillaGoalSpec;
    private final VanillaGoalKey vanillaGoalKey;
    private final CustomGoalSpec customGoalSpec;
    private final CustomGoalKey customGoalKey;

    private RuntimeGoalMutation(
            @NotNull Operation operation,
            @NotNull GoalSelectorType selectorType,
            @Nullable VanillaGoalSpec vanillaGoalSpec,
            @Nullable VanillaGoalKey vanillaGoalKey,
            @Nullable CustomGoalSpec customGoalSpec,
            @Nullable CustomGoalKey customGoalKey
    ) {
        this.operation = Objects.requireNonNull(operation, "operation cannot be null");
        this.selectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
        this.vanillaGoalSpec = vanillaGoalSpec;
        this.vanillaGoalKey = vanillaGoalKey;
        this.customGoalSpec = customGoalSpec;
        this.customGoalKey = customGoalKey;
    }

    public static @NotNull RuntimeGoalMutation addVanilla(@NotNull VanillaGoalSpec goalSpec) {
        VanillaGoalSpec resolvedGoalSpec = Objects.requireNonNull(goalSpec, "goalSpec cannot be null");
        return new RuntimeGoalMutation(
                Operation.ADD_VANILLA,
                resolvedGoalSpec.selectorType(),
                resolvedGoalSpec,
                null,
                null,
                null
        );
    }

    public static @NotNull RuntimeGoalMutation removeVanilla(
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key
    ) {
        return new RuntimeGoalMutation(
                Operation.REMOVE_VANILLA,
                Objects.requireNonNull(selectorType, "selectorType cannot be null"),
                null,
                Objects.requireNonNull(key, "key cannot be null"),
                null,
                null
        );
    }

    public static @NotNull RuntimeGoalMutation addCustom(@NotNull CustomGoalSpec goalSpec) {
        CustomGoalSpec resolvedGoalSpec = Objects.requireNonNull(goalSpec, "goalSpec cannot be null");
        return new RuntimeGoalMutation(
                Operation.ADD_CUSTOM,
                resolvedGoalSpec.selectorType(),
                null,
                null,
                resolvedGoalSpec,
                null
        );
    }

    public static @NotNull RuntimeGoalMutation removeCustom(
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key
    ) {
        return new RuntimeGoalMutation(
                Operation.REMOVE_CUSTOM,
                Objects.requireNonNull(selectorType, "selectorType cannot be null"),
                null,
                null,
                null,
                Objects.requireNonNull(key, "key cannot be null")
        );
    }

    public static @NotNull RuntimeGoalMutation clear(@NotNull GoalSelectorType selectorType) {
        return new RuntimeGoalMutation(
                Operation.CLEAR_SELECTOR,
                Objects.requireNonNull(selectorType, "selectorType cannot be null"),
                null,
                null,
                null,
                null
        );
    }

    public @NotNull Operation operation() {
        return operation;
    }

    public @NotNull GoalSelectorType selectorType() {
        return selectorType;
    }

    public @Nullable VanillaGoalSpec vanillaGoalSpecOrNull() {
        return vanillaGoalSpec;
    }

    public @Nullable VanillaGoalKey vanillaGoalKeyOrNull() {
        return vanillaGoalKey;
    }

    public @Nullable CustomGoalSpec customGoalSpecOrNull() {
        return customGoalSpec;
    }

    public @Nullable CustomGoalKey customGoalKeyOrNull() {
        return customGoalKey;
    }
}
