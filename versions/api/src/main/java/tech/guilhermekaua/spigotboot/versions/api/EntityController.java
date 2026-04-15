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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base controller used to intercept native entity hooks.
 *
 * @param <T> the Bukkit entity type exposed to plugin code
 * @since 2.0.2
 */
public abstract class EntityController<T extends Entity> {

    /**
     * Invoked after the entity has been spawned and initialized.
     */
    public void onSpawn() {
    }

    /**
     * Invoked after the entity has been spawned and initialized.
     *
     * @param entity the live spawned entity
     */
    public void onSpawn(@NotNull SpawnedEntity<T> entity) {
        onSpawn();
    }

    /**
     * Invoked when the native entity ticks.
     */
    public void onTick() {
    }

    /**
     * Invoked when the native entity ticks.
     *
     * @param context the hook context
     */
    public void onTick(@NotNull EntityTickContext<T> context) {
        onTick();
    }

    /**
     * Invoked when the native entity moves.
     *
     * @param x the X movement
     * @param y the Y movement
     * @param z the Z movement
     */
    public void onMove(double x, double y, double z) {
    }

    /**
     * Invoked when the native entity moves.
     *
     * @param context the hook context
     */
    public void onMove(@NotNull EntityMoveContext<T> context) {
        onMove(context.x(), context.y(), context.z());
    }

    /**
     * Invoked when the native entity is pushed by a motion vector.
     *
     * @param x the X push
     * @param y the Y push
     * @param z the Z push
     */
    public void onPush(double x, double y, double z) {
    }

    /**
     * Invoked when the native entity is pushed by a motion vector.
     *
     * @param context the hook context
     */
    public void onPush(@NotNull EntityPushContext<T> context) {
        onPush(context.x(), context.y(), context.z());
    }

    /**
     * Invoked when the native entity is damaged.
     *
     * @param amount the incoming damage amount
     */
    public void onDamage(float amount) {
    }

    /**
     * Invoked when the native entity is damaged.
     *
     * @param context the hook context
     */
    public void onDamage(@NotNull EntityDamageContext<T> context) {
        onDamage(context.amount());
    }

    /**
     * Invoked when the native entity is interacted with by a player.
     *
     * @param player the interacting player
     * @param hand the interacting hand
     */
    public void onInteract(@NotNull Player player, @NotNull EntityInteractionHand hand) {
    }

    /**
     * Invoked when the native entity is interacted with by a player.
     *
     * @param context the hook context
     */
    public void onInteract(@NotNull EntityInteractContext<T> context) {
        onInteract(context.player(), context.hand());
    }

    /**
     * Invoked when the native entity dies.
     */
    public void onDie() {
    }

    /**
     * Invoked when the native entity dies.
     *
     * @param context the hook context
     */
    public void onDie(@NotNull EntityDieContext<T> context) {
        onDie();
    }

    /**
     * Invoked when the native entity is removed from the world.
     */
    public void onRemove() {
    }

    /**
     * Invoked when the native entity is removed from the world.
     *
     * @param context the hook context
     */
    public void onRemove(@NotNull EntityRemoveContext<T> context) {
        onRemove();
    }

    /**
     * Invoked when the native entity collides with another entity.
     *
     * @param entity the other Bukkit entity
     */
    public void onCollide(@NotNull Entity entity) {
    }

    /**
     * Invoked when the native entity collides with another entity.
     *
     * @param context the hook context
     */
    public void onCollide(@NotNull EntityCollideContext<T> context) {
        onCollide(context.collidingEntity());
    }

    /**
     * Invoked when the native entity positions its passenger.
     *
     * @param passenger the passenger being positioned, or {@code null} when unavailable
     */
    public void onPositionPassenger(@Nullable Entity passenger) {
    }

    /**
     * Invoked when the native entity positions its passenger.
     *
     * @param context the hook context
     */
    public void onPositionPassenger(@NotNull EntityPositionPassengerContext<T> context) {
        onPositionPassenger(context.passenger());
    }

    /**
     * Invoked when an equipment or hand slot changes.
     *
     * @param slot the logical slot
     * @param previousItem the previous item, or {@code null}
     * @param newItem the new item, or {@code null}
     */
    public void onInventoryChange(
            @NotNull EntityEquipmentSlot slot,
            @Nullable ItemStack previousItem,
            @Nullable ItemStack newItem
    ) {
    }

    /**
     * Invoked when an equipment or hand slot changes.
     *
     * @param context the hook context
     */
    public void onInventoryChange(@NotNull EntityInventoryChangeContext<T> context) {
        onInventoryChange(context.slot(), context.previousItem(), context.newItem());
    }
}
