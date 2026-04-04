/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
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
package tech.guilhermekaua.spigotboot.entity.api.capability;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.exception.UnsupportedGoalException;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKey;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKey;

import java.util.Set;

/**
 * Exposes the goal support matrix for a specific version adapter.
 *
 * @since 2.0.2
 */
public interface EntityCapabilities {

    /**
     * Returns the supported goals for a logical entity type.
     *
     * @param entityType the entity type to inspect
     * @return the supported goals
     */
    @NotNull Set<EntityGoalKey> supportedGoals(@NotNull EntityTypeKey entityType);

    /**
     * Checks whether a goal is supported for a logical entity type.
     *
     * @param entityType the entity type to inspect
     * @param goalKey the goal to inspect
     * @return {@code true} when the goal is supported
     */
    default boolean supportsGoal(@NotNull EntityTypeKey entityType, @NotNull EntityGoalKey goalKey) {
        return supportedGoals(entityType).contains(goalKey);
    }

    /**
     * Ensures that a goal is supported for the supplied version and entity type.
     *
     * @param version the active Minecraft version
     * @param entityType the logical entity type
     * @param goalKey the goal to validate
     * @throws UnsupportedGoalException when the goal is unavailable
     */
    default void requireGoal(
            @NotNull MinecraftVersion version,
            @NotNull EntityTypeKey entityType,
            @NotNull EntityGoalKey goalKey
    ) {
        if (!supportsGoal(entityType, goalKey)) {
            throw new UnsupportedGoalException(
                    "Goal '" + goalKey + "' is not supported for entity '" + entityType + "' on Minecraft " + version + "."
            );
        }
    }
}
