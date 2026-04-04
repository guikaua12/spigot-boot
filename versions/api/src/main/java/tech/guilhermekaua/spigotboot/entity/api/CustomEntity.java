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
package tech.guilhermekaua.spigotboot.entity.api;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKey;
import tech.guilhermekaua.spigotboot.entity.api.goal.GoalDefinition;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKey;

/**
 * Represents a custom entity instance backed by a version-specific implementation.
 *
 * @since 2.0.2
 */
public interface CustomEntity {

    /**
     * Returns the logical entity type exposed by the library.
     *
     * @return the entity type key
     */
    @NotNull EntityTypeKey type();

    /**
     * Returns the Minecraft version that created this entity.
     *
     * @return the version that owns this entity
     */
    @NotNull MinecraftVersion version();

    /**
     * Adds a goal to the entity.
     *
     * @param goalDefinition the goal definition to add
     * @throws NullPointerException when the goal definition is null
     * @throws RuntimeException when the implementation cannot apply the goal
     */
    void addGoal(@NotNull GoalDefinition goalDefinition);

    /**
     * Removes a goal from the entity.
     *
     * @param goalKey the goal to remove
     * @throws NullPointerException when the goal key is null
     */
    void removeGoal(@NotNull EntityGoalKey goalKey);

    /**
     * Clears all registered goals from the entity.
     */
    void clearGoals();

    /**
     * Moves the entity towards a location.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param speed the pathfinding speed
     */
    void moveTo(double x, double y, double z, double speed);

    /**
     * Despawns the entity.
     */
    void despawn();

    /**
     * Returns the native implementation object.
     *
     * @return the underlying implementation object
     */
    @NotNull Object unwrap();
}
