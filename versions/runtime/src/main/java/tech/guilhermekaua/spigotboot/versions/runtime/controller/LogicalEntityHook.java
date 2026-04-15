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
package tech.guilhermekaua.spigotboot.versions.runtime.controller;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.versions.api.EntityCollideContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityDamageContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityDieContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityEquipmentSlot;
import tech.guilhermekaua.spigotboot.versions.api.EntityInteractContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityInteractionHand;
import tech.guilhermekaua.spigotboot.versions.api.EntityInventoryChangeContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityMoveContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityPositionPassengerContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityPushContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;

import java.lang.reflect.Method;

public enum LogicalEntityHook {
    TICK("onTick", EntityTickContext.class),
    MOVE("onMove", EntityMoveContext.class, double.class, double.class, double.class),
    PUSH("onPush", EntityPushContext.class, double.class, double.class, double.class),
    DAMAGE("onDamage", EntityDamageContext.class, float.class),
    INTERACT("onInteract", EntityInteractContext.class, Player.class, EntityInteractionHand.class),
    DIE("onDie", EntityDieContext.class),
    REMOVE("onRemove", EntityRemoveContext.class),
    COLLIDE("onCollide", EntityCollideContext.class, Entity.class),
    POSITION_PASSENGER("onPositionPassenger", EntityPositionPassengerContext.class, Entity.class),
    INVENTORY_CHANGE(
            "onInventoryChange",
            EntityInventoryChangeContext.class,
            EntityEquipmentSlot.class,
            ItemStack.class,
            ItemStack.class
    );

    private final String methodName;
    private final Class<?> contextType;
    private final Class<?>[] convenienceParameterTypes;

    LogicalEntityHook(String methodName, Class<?> contextType, Class<?>... convenienceParameterTypes) {
        this.methodName = methodName;
        this.contextType = contextType;
        this.convenienceParameterTypes = convenienceParameterTypes;
    }

    public Method resolveContextMethod(Class<?> controllerType) throws NoSuchMethodException {
        return controllerType.getMethod(methodName, contextType);
    }

    public Method resolveConvenienceMethod(Class<?> controllerType) throws NoSuchMethodException {
        return controllerType.getMethod(methodName, convenienceParameterTypes);
    }

    public Class<?> contextType() {
        return contextType;
    }

    public String methodName() {
        return methodName;
    }

    public Class<?>[] convenienceParameterTypes() {
        return convenienceParameterTypes;
    }
}
