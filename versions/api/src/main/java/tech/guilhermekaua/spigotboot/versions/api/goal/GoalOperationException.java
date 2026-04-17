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

import java.util.Objects;

/**
 * Base exception for explicit managed goal mutation failures.
 *
 * @since 2.0.2
 */
public class GoalOperationException extends RuntimeException {
    private final GoalSelectorType selectorType;
    private final VanillaGoalKey vanillaKey;
    private final CustomGoalKey customKey;
    private final Class<? extends Entity> entityType;
    private final MinecraftVersion minecraftVersion;

    /**
     * Creates an exception for a selector-wide goal operation.
     *
     * @param message the failure message
     * @param selectorType the selector involved in the operation
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public GoalOperationException(
            @NotNull String message,
            @NotNull GoalSelectorType selectorType,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        this(message, null, selectorType, null, null, entityType, minecraftVersion);
    }

    /**
     * Creates an exception for a selector-wide goal operation.
     *
     * @param message the failure message
     * @param cause the failure cause, or {@code null}
     * @param selectorType the selector involved in the operation
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public GoalOperationException(
            @NotNull String message,
            @Nullable Throwable cause,
            @NotNull GoalSelectorType selectorType,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        this(message, cause, selectorType, null, null, entityType, minecraftVersion);
    }

    /**
     * Creates an exception for a vanilla goal operation.
     *
     * @param message the failure message
     * @param selectorType the selector involved in the operation
     * @param key the managed vanilla goal key
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public GoalOperationException(
            @NotNull String message,
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        this(message, null, selectorType, key, null, entityType, minecraftVersion);
    }

    /**
     * Creates an exception for a vanilla goal operation.
     *
     * @param message the failure message
     * @param cause the failure cause, or {@code null}
     * @param selectorType the selector involved in the operation
     * @param key the managed vanilla goal key
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public GoalOperationException(
            @NotNull String message,
            @Nullable Throwable cause,
            @NotNull GoalSelectorType selectorType,
            @NotNull VanillaGoalKey key,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        this(message, cause, selectorType, key, null, entityType, minecraftVersion);
    }

    /**
     * Creates an exception for a custom goal operation.
     *
     * @param message the failure message
     * @param selectorType the selector involved in the operation
     * @param key the managed custom goal key
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public GoalOperationException(
            @NotNull String message,
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        this(message, null, selectorType, null, key, entityType, minecraftVersion);
    }

    /**
     * Creates an exception for a custom goal operation.
     *
     * @param message the failure message
     * @param cause the failure cause, or {@code null}
     * @param selectorType the selector involved in the operation
     * @param key the managed custom goal key
     * @param entityType the Bukkit entity type involved, or {@code null}
     * @param minecraftVersion the Minecraft version involved, or {@code null}
     */
    public GoalOperationException(
            @NotNull String message,
            @Nullable Throwable cause,
            @NotNull GoalSelectorType selectorType,
            @NotNull CustomGoalKey key,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        this(message, cause, selectorType, null, key, entityType, minecraftVersion);
    }

    private GoalOperationException(
            @NotNull String message,
            @Nullable Throwable cause,
            @NotNull GoalSelectorType selectorType,
            @Nullable VanillaGoalKey vanillaKey,
            @Nullable CustomGoalKey customKey,
            @Nullable Class<? extends Entity> entityType,
            @Nullable MinecraftVersion minecraftVersion
    ) {
        super(Objects.requireNonNull(message, "message cannot be null"), cause);
        this.selectorType = Objects.requireNonNull(selectorType, "selectorType cannot be null");
        if (vanillaKey != null && customKey != null) {
            throw new IllegalArgumentException("At most one managed goal key may be provided.");
        }
        this.vanillaKey = vanillaKey;
        this.customKey = customKey;
        this.entityType = entityType;
        this.minecraftVersion = minecraftVersion;
    }

    /**
     * Returns the selector involved in the operation.
     *
     * @return the selector involved in the operation
     */
    public @NotNull GoalSelectorType selectorType() {
        return selectorType;
    }

    /**
     * Returns the managed vanilla goal key, or {@code null} when the failure targeted a custom key.
     *
     * @return the managed vanilla goal key, or {@code null}
     */
    public @Nullable VanillaGoalKey vanillaKeyOrNull() {
        return vanillaKey;
    }

    /**
     * Returns the managed custom goal key, or {@code null} when the failure targeted a vanilla key.
     *
     * @return the managed custom goal key, or {@code null}
     */
    public @Nullable CustomGoalKey customKeyOrNull() {
        return customKey;
    }

    /**
     * Returns the Bukkit entity type involved in the failure, or {@code null} when not known.
     *
     * @return the Bukkit entity type involved in the failure, or {@code null}
     */
    public @Nullable Class<? extends Entity> entityTypeOrNull() {
        return entityType;
    }

    /**
     * Returns the Minecraft version involved in the failure, or {@code null} when not known.
     *
     * @return the Minecraft version involved in the failure, or {@code null}
     */
    public @Nullable MinecraftVersion minecraftVersionOrNull() {
        return minecraftVersion;
    }
}
