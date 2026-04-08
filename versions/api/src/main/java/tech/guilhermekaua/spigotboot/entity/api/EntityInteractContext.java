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

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Hook context exposed to {@link EntityController#onInteract(EntityInteractContext)}.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public final class EntityInteractContext<T extends Entity>
        extends AbstractEntityHookContext<T, EntityInteractionResult> {
    private final Player player;
    private final EntityInteractionHand hand;

    /**
     * Creates a new interaction hook context.
     *
     * @param entity the controlled entity
     * @param base the vanilla base invoker
     * @param player the interacting player
     * @param hand the interacting hand
     */
    public EntityInteractContext(
            @NotNull ControlledEntity<T> entity,
            @NotNull EntityBaseInvoker<EntityInteractionResult> base,
            @NotNull Player player,
            @NotNull EntityInteractionHand hand
    ) {
        super(entity, base);
        this.player = Objects.requireNonNull(player, "player cannot be null");
        this.hand = Objects.requireNonNull(hand, "hand cannot be null");
    }

    /**
     * Returns the interacting player.
     *
     * @return the interacting player
     */
    public @NotNull Player player() {
        return player;
    }

    /**
     * Returns the interacting hand.
     *
     * @return the interacting hand
     */
    public @NotNull EntityInteractionHand hand() {
        return hand;
    }
}
