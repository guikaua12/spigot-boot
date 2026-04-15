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
package tech.guilhermekaua.spigotboot.versions.api;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Hook context exposed to {@link EntityController#onCollide(EntityCollideContext)}.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class EntityCollideContext<T extends Entity> extends AbstractEntityHookContext<T, Void> {
    private final Entity collidingEntity;

    /**
     * Creates a new collision hook context.
     *
     * @param entity the controlled entity
     * @param base the vanilla base invoker
     * @param collidingEntity the colliding Bukkit entity
     */
    public EntityCollideContext(
            @NotNull ControlledEntity<T> entity,
            @NotNull EntityBaseInvoker<Void> base,
            @NotNull Entity collidingEntity
    ) {
        super(entity, base);
        this.collidingEntity = Objects.requireNonNull(collidingEntity, "collidingEntity cannot be null");
    }

    /**
     * Returns the colliding Bukkit entity.
     *
     * @return the colliding Bukkit entity
     */
    public @NotNull Entity collidingEntity() {
        return collidingEntity;
    }
}
