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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a live hooked entity whose native lifecycle is delegated through a controller.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public interface ControlledEntity<T extends LivingEntity> {

    /**
     * Returns the Bukkit wrapper backed by the hooked native entity.
     *
     * @return the Bukkit entity
     */
    @NotNull T bukkitEntity();

    /**
     * Returns the mutable state bag associated with this entity.
     *
     * @return the entity state bag
     */
    @NotNull CustomEntityState state();

    /**
     * Removes the entity from the world.
     */
    void remove();

    /**
     * Returns whether the entity has already been removed.
     *
     * @return {@code true} when the entity has been removed
     */
    boolean isRemoved();

    /**
     * Returns the logical vanilla base type represented by this controlled entity.
     *
     * @return the logical base type
     */
    @NotNull CustomEntityBaseType baseType();

    /**
     * Returns the resolved Minecraft version hosting this entity.
     *
     * @return the resolved Minecraft version
     */
    @NotNull MinecraftVersion minecraftVersion();

    /**
     * Returns the active controller currently handling native hooks for this entity.
     *
     * @return the active controller
     */
    @NotNull EntityController<T> controller();

    /**
     * Replaces the active controller.
     *
     * @param controller the new controller
     */
    void setController(@NotNull EntityController<T> controller);

    /**
     * Resets this entity to the default pass-through controller.
     */
    void clearController();

    /**
     * Returns whether this entity is still backed by a hooked native handle.
     *
     * @return {@code true} when the entity is still hooked
     */
    boolean isHooked();
}
