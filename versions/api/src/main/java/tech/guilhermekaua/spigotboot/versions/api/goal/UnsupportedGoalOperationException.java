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

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;

/**
 * Signals that an entity, version, or managed key combination is unsupported.
 *
 * @since 2.0.2
 */
public final class UnsupportedGoalOperationException extends GoalOperationException {

    /**
     * Creates an exception for an unsupported selector-wide goal operation.
     *
     * @param message the failure message
     * @param selectorType the selector involved in the operation
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public UnsupportedGoalOperationException(
            @NotNull String message,
            @NotNull GoalSelectorType selectorType,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        super(message, selectorType, entityType, minecraftVersion);
    }

    /**
     * Creates an exception for an unsupported vanilla goal operation.
     *
     * @param message the failure message
     * @param selectorType the selector involved in the operation
     * @param key the managed vanilla goal key
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public UnsupportedGoalOperationException(
            @NotNull String message,
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        super(message, selectorType, key, entityType, minecraftVersion);
    }

    /**
     * Creates an exception for an unsupported custom goal operation.
     *
     * @param message the failure message
     * @param selectorType the selector involved in the operation
     * @param key the managed custom goal key
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public UnsupportedGoalOperationException(
            @NotNull String message,
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        super(message, selectorType, key, entityType, minecraftVersion);
    }
}
