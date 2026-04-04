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
package tech.guilhermekaua.spigotboot.entity.api.zombie;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntity;

/**
 * Typed view of a zombie entity backed by a version-specific NMS implementation.
 *
 * <p>This interface exposes only operations that are available and meaningful across
 * all targeted Minecraft versions. For version-specific NMS access, call
 * {@link #unwrap()} to obtain the underlying Bukkit entity.
 *
 * @since 2.0.2
 */
public interface CustomZombie extends CustomEntity {

    /**
     * Returns whether this zombie is in baby form.
     *
     * @return {@code true} when the zombie is a baby
     */
    boolean isBaby();

    /**
     * Sets the baby state of this zombie.
     *
     * @param baby {@code true} to make the zombie a baby
     */
    void setBaby(boolean baby);

    /**
     * Returns the custom display name of this zombie, or {@code null} when none has been set.
     *
     * @return the custom display name, or {@code null}
     */
    @Nullable String getName();

    /**
     * Sets the custom display name of this zombie.
     *
     * @param name the name to set, or {@code null} to clear the name
     */
    void setName(@Nullable String name);

    /**
     * Sets whether the custom name tag is always visible above the zombie's head.
     *
     * @param visible {@code true} to make the name tag visible at all times
     */
    void setNameVisible(boolean visible);

    /**
     * Returns the current health of this zombie.
     *
     * @return the current health value
     */
    double getHealth();

    /**
     * Sets the current health of this zombie.
     *
     * @param health the health value; must be between 0 and {@link #getMaxHealth()}
     * @throws IllegalArgumentException when the value is negative or exceeds the maximum health
     */
    void setHealth(double health);

    /**
     * Returns the maximum health of this zombie.
     *
     * @return the maximum health value
     */
    double getMaxHealth();

    /**
     * Sets the maximum health of this zombie.
     *
     * @param maxHealth the maximum health; must be positive
     * @throws IllegalArgumentException when the value is not positive
     */
    void setMaxHealth(double maxHealth);
}
