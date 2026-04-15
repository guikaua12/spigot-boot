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
package tech.guilhermekaua.spigotboot.v1_19_2.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.EntityCollideContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityDamageContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityDieContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityEquipmentSlot;
import tech.guilhermekaua.spigotboot.versions.api.EntityInteractContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityInteractionHand;
import tech.guilhermekaua.spigotboot.versions.api.EntityInteractionResult;
import tech.guilhermekaua.spigotboot.versions.api.EntityInventoryChangeContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityMoveContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityPositionPassengerContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityPushContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

final class EntityHookBinderV1_19_2 implements NativeHookBinder<Entity> {
    private static final String HOOK_TICK = "tick";
    private static final String HOOK_MOVE = "move";
    private static final String HOOK_PUSH = "push";
    private static final String HOOK_DAMAGE = "damage";
    private static final String HOOK_INTERACT = "interact";
    private static final String HOOK_DIE = "die";
    private static final String HOOK_REMOVE = "remove";
    private static final String HOOK_COLLIDE = "collide";
    private static final String HOOK_POSITION_PASSENGER = "positionPassenger";
    private static final String HOOK_INVENTORY_CHANGE = "inventoryChange";
    private static final Object[] EMPTY_ARGUMENTS = new Object[0];

    @Override
    public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs(@NotNull Class<?> nativeType) {
        Objects.requireNonNull(nativeType, "nativeType cannot be null");
        return resolveHookSpecs(nativeType);
    }

    @Override
    public @Nullable Object dispatch(
            @NotNull AbstractRuntimeControlledEntity<Entity> controlledEntity,
            @NotNull LifecycleAwareNativeEntity nativeEntity,
            @NotNull String hookName,
            @Nullable Object[] arguments
    ) {
        Objects.requireNonNull(controlledEntity, "controlledEntity cannot be null");
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        Objects.requireNonNull(hookName, "hookName cannot be null");

        if (HOOK_TICK.equals(hookName)) {
            controlledEntity.dispatchTick(new ContextualBaseInvoker<EntityTickContext<Entity>, Void>() {
                @Override
                public Void invoke(@NotNull EntityTickContext<Entity> context) {
                    nativeEntity.spigotBootInvokeBase(HOOK_TICK, EMPTY_ARGUMENTS);
                    return null;
                }
            });
            return null;
        }

        if (HOOK_MOVE.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 2);
            controlledEntity.dispatchMove(
                    resolveVecCoordinate(rawArguments[1], "x"),
                    resolveVecCoordinate(rawArguments[1], "y"),
                    resolveVecCoordinate(rawArguments[1], "z"),
                    new ContextualBaseInvoker<EntityMoveContext<Entity>, Void>() {
                        @Override
                        public Void invoke(@NotNull EntityMoveContext<Entity> context) {
                            nativeEntity.spigotBootInvokeBase(
                                    HOOK_MOVE,
                                    new Object[]{rawArguments[0], createVec3(context.x(), context.y(), context.z())}
                            );
                            return null;
                        }
                    }
            );
            return null;
        }

        if (HOOK_PUSH.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 3);
            controlledEntity.dispatchPush(
                    ((Double) rawArguments[0]).doubleValue(),
                    ((Double) rawArguments[1]).doubleValue(),
                    ((Double) rawArguments[2]).doubleValue(),
                    new ContextualBaseInvoker<EntityPushContext<Entity>, Void>() {
                        @Override
                        public Void invoke(@NotNull EntityPushContext<Entity> context) {
                            nativeEntity.spigotBootInvokeBase(
                                    HOOK_PUSH,
                                    new Object[]{
                                            Double.valueOf(context.x()),
                                            Double.valueOf(context.y()),
                                            Double.valueOf(context.z())
                                    }
                            );
                            return null;
                        }
                    }
            );
            return null;
        }

        if (HOOK_DAMAGE.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 2);
            return Boolean.valueOf(controlledEntity.dispatchDamage(
                    ((Float) rawArguments[1]).floatValue(),
                    new ContextualBaseInvoker<EntityDamageContext<Entity>, Boolean>() {
                        @Override
                        public Boolean invoke(@NotNull EntityDamageContext<Entity> context) {
                            Object result = nativeEntity.spigotBootInvokeBase(
                                    HOOK_DAMAGE,
                                    new Object[]{rawArguments[0], Float.valueOf(context.amount())}
                            );
                            return Boolean.valueOf(result != null && ((Boolean) result).booleanValue());
                        }
                    }
            ));
        }

        if (HOOK_INTERACT.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 2);
            final Player player = (Player) resolveBukkitEntity(rawArguments[0]);
            final Object[] baseInteractionResultHolder = new Object[1];
            EntityInteractionResult result = controlledEntity.dispatchInteract(
                    player,
                    toApiHand(rawArguments[1]),
                    new ContextualBaseInvoker<EntityInteractContext<Entity>, EntityInteractionResult>() {
                        @Override
                        public EntityInteractionResult invoke(@NotNull EntityInteractContext<Entity> context) {
                            Object baseResult = nativeEntity.spigotBootInvokeBase(
                                    HOOK_INTERACT,
                                    new Object[]{rawArguments[0], rawArguments[1]}
                            );
                            baseInteractionResultHolder[0] = baseResult;
                            return toApiInteractionResult(baseResult);
                        }
                    }
            );
            return toNmsInteractionResult(result, baseInteractionResultHolder[0]);
        }

        if (HOOK_DIE.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 1);
            controlledEntity.dispatchDie(new ContextualBaseInvoker<EntityDieContext<Entity>, Void>() {
                @Override
                public Void invoke(@NotNull EntityDieContext<Entity> context) {
                    nativeEntity.spigotBootInvokeBase(HOOK_DIE, rawArguments);
                    return null;
                }
            });
            return null;
        }

        if (HOOK_REMOVE.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 1);
            controlledEntity.dispatchRemove(new ContextualBaseInvoker<EntityRemoveContext<Entity>, Void>() {
                @Override
                public Void invoke(@NotNull EntityRemoveContext<Entity> context) {
                    nativeEntity.spigotBootInvokeBase(HOOK_REMOVE, rawArguments);
                    return null;
                }
            });
            return null;
        }

        if (HOOK_COLLIDE.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 1);
            controlledEntity.dispatchCollide(
                    resolveBukkitEntity(rawArguments[0]),
                    new ContextualBaseInvoker<EntityCollideContext<Entity>, Void>() {
                        @Override
                        public Void invoke(@NotNull EntityCollideContext<Entity> context) {
                            nativeEntity.spigotBootInvokeBase(HOOK_COLLIDE, rawArguments);
                            return null;
                        }
                    }
            );
            return null;
        }

        if (HOOK_POSITION_PASSENGER.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 1, 2);
            controlledEntity.dispatchPositionPassenger(
                    resolveBukkitEntity(rawArguments[0]),
                    new ContextualBaseInvoker<EntityPositionPassengerContext<Entity>, Void>() {
                        @Override
                        public Void invoke(@NotNull EntityPositionPassengerContext<Entity> context) {
                            nativeEntity.spigotBootInvokeBase(HOOK_POSITION_PASSENGER, rawArguments);
                            return null;
                        }
                    }
            );
            return null;
        }

        if (HOOK_INVENTORY_CHANGE.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 2, 3);
            final Object nativeSlot = rawArguments[0];
            final ItemStack previousItem = rawArguments.length == 3
                    ? toBukkitItem(rawArguments[1])
                    : toBukkitItem(resolveCurrentEquipmentItem(nativeEntity, nativeSlot));
            final ItemStack newItem = toBukkitItem(rawArguments[rawArguments.length - 1]);

            controlledEntity.dispatchInventoryChange(
                    toApiEquipmentSlot(nativeSlot),
                    previousItem,
                    newItem,
                    new ContextualBaseInvoker<EntityInventoryChangeContext<Entity>, Void>() {
                        @Override
                        public Void invoke(@NotNull EntityInventoryChangeContext<Entity> context) {
                            if (rawArguments.length == 3) {
                                nativeEntity.spigotBootInvokeBase(
                                        HOOK_INVENTORY_CHANGE,
                                        new Object[]{
                                                toNmsEquipmentSlot(context.slot()),
                                                toNmsItem(context.previousItem()),
                                                toNmsItem(context.newItem())
                                        }
                                );
                                return null;
                            }

                            nativeEntity.spigotBootInvokeBase(
                                    HOOK_INVENTORY_CHANGE,
                                    new Object[]{
                                            toNmsEquipmentSlot(context.slot()),
                                            toNmsItem(context.newItem())
                                    }
                            );
                            return null;
                        }
                    }
            );
            return null;
        }

        throw new IllegalArgumentException("Unknown 1.19.2 Entity hook: " + hookName);
    }

    private @NotNull Collection<GeneratedNativeHookSpec> resolveHookSpecs(@NotNull Class<?> nativeType) {
        List<GeneratedNativeHookSpec> hookSpecs = new ArrayList<GeneratedNativeHookSpec>();
        addHookSpec(hookSpecs, HOOK_TICK, ReflectionSupport.findNamedMethod(nativeType, new String[]{"tick"}));

        Class<?> moverType = findClass("net.minecraft.world.entity.MoverType");
        Class<?> vec3Type = findClass("net.minecraft.world.phys.Vec3");
        if (moverType != null && vec3Type != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_MOVE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"move"}, moverType, vec3Type)
            );
        }

        addHookSpec(
                hookSpecs,
                HOOK_PUSH,
                ReflectionSupport.findNamedMethod(nativeType, new String[]{"push"}, double.class, double.class, double.class)
        );

        Class<?> damageSourceType = findClass("net.minecraft.world.damagesource.DamageSource");
        if (damageSourceType != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_DAMAGE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"hurt"}, damageSourceType, float.class)
            );
            addHookSpec(
                    hookSpecs,
                    HOOK_DIE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"die"}, damageSourceType)
            );
        }

        Class<?> playerType = findClass("net.minecraft.world.entity.player.Player");
        Class<?> interactionHandType = findClass("net.minecraft.world.InteractionHand");
        if (playerType != null && interactionHandType != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_INTERACT,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"interact"}, playerType, interactionHandType)
            );
        }

        Class<?> removalReasonType = findClass("net.minecraft.world.entity.Entity$RemovalReason");
        if (removalReasonType != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_REMOVE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"remove"}, removalReasonType)
            );
        }

        Class<?> entityType = findClass("net.minecraft.world.entity.Entity");
        if (entityType != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_COLLIDE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"push"}, entityType)
            );

            Method positionPassengerMethod = null;
            Class<?> moveFunctionType = findClass("net.minecraft.world.entity.Entity$MoveFunction");
            if (moveFunctionType != null) {
                positionPassengerMethod = ReflectionSupport.findNamedMethod(
                        nativeType,
                        new String[]{"positionRider"},
                        entityType,
                        moveFunctionType
                );
            }
            if (positionPassengerMethod == null) {
                positionPassengerMethod = ReflectionSupport.findNamedMethod(
                        nativeType,
                        new String[]{"positionRider"},
                        entityType
                );
            }
            addHookSpec(hookSpecs, HOOK_POSITION_PASSENGER, positionPassengerMethod);
        }

        Class<?> equipmentSlotType = findClass("net.minecraft.world.entity.EquipmentSlot");
        Class<?> itemStackType = findClass("net.minecraft.world.item.ItemStack");
        if (equipmentSlotType != null && itemStackType != null) {
            Method inventoryChangeMethod = ReflectionSupport.findNamedMethod(
                    nativeType,
                    new String[]{"onEquipItem"},
                    equipmentSlotType,
                    itemStackType,
                    itemStackType
            );
            if (inventoryChangeMethod == null) {
                inventoryChangeMethod = ReflectionSupport.findNamedMethod(
                        nativeType,
                        new String[]{"setItemSlot"},
                        equipmentSlotType,
                        itemStackType
                );
            }
            addHookSpec(hookSpecs, HOOK_INVENTORY_CHANGE, inventoryChangeMethod);
        }

        return hookSpecs;
    }

    private static void addHookSpec(
            @NotNull Collection<GeneratedNativeHookSpec> hookSpecs,
            @NotNull String hookName,
            @Nullable Method method
    ) {
        if (method != null) {
            hookSpecs.add(GeneratedNativeHookSpec.of(hookName, method));
        }
    }

    private static @NotNull Object[] requireArguments(@Nullable Object[] arguments, int... expectedLengths) {
        if (arguments != null) {
            for (int expectedLength : expectedLengths) {
                if (arguments.length == expectedLength) {
                    return arguments;
                }
            }
        }

        StringBuilder lengths = new StringBuilder();
        for (int index = 0; index < expectedLengths.length; index++) {
            if (index > 0) {
                lengths.append(" or ");
            }
            lengths.append(expectedLengths[index]);
        }
        throw new IllegalArgumentException(
                "Expected " + lengths + " native hook arguments but received "
                        + (arguments == null ? 0 : arguments.length) + "."
        );
    }

    private static double resolveVecCoordinate(@NotNull Object vec3, @NotNull String fieldName) {
        Field field = ReflectionSupport.requireField(vec3.getClass(), fieldName);
        return ((Double) ReflectionSupport.readField(field, vec3)).doubleValue();
    }

    private static @NotNull Object createVec3(double x, double y, double z) {
        Class<?> vec3Type = ReflectionSupport.requireClass("net.minecraft.world.phys.Vec3");
        Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(
                vec3Type,
                double.class,
                double.class,
                double.class
        );
        return ReflectionSupport.instantiate(constructor, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
    }

    private static @NotNull Entity resolveBukkitEntity(@NotNull Object nativeEntity) {
        Method getBukkitEntityMethod = ReflectionSupport.requireNamedMethod(
                nativeEntity.getClass(),
                new String[]{"getBukkitEntity"}
        );
        return (Entity) ReflectionSupport.invoke(getBukkitEntityMethod, nativeEntity);
    }

    private static @NotNull EntityInteractionHand toApiHand(@NotNull Object hand) {
        return "OFF_HAND".equals(((Enum<?>) hand).name())
                ? EntityInteractionHand.OFF_HAND
                : EntityInteractionHand.MAIN_HAND;
    }

    private static @NotNull EntityInteractionResult toApiInteractionResult(@Nullable Object interactionResult) {
        if (interactionResult == null) {
            return EntityInteractionResult.PASS;
        }

        if (interactionResult.equals(readInteractionResult("FAIL", "e"))) {
            return EntityInteractionResult.FAIL;
        }
        if (interactionResult.equals(readInteractionResult("PASS", "d"))) {
            return EntityInteractionResult.PASS;
        }
        if (interactionResult.equals(readInteractionResult("CONSUME", "b"))
                || interactionResult.equals(readInteractionResult("CONSUME_PARTIAL", "c"))) {
            return EntityInteractionResult.CONSUME;
        }
        if (interactionResult.equals(readInteractionResult("SUCCESS", "a"))) {
            return EntityInteractionResult.SUCCESS;
        }
        return EntityInteractionResult.PASS;
    }

    private static @NotNull Object toNmsInteractionResult(
            @NotNull EntityInteractionResult result,
            @Nullable Object baseInteractionResult
    ) {
        Class<?> interactionResultType = ReflectionSupport.requireClass("net.minecraft.world.InteractionResult");
        if (baseInteractionResult != null
                && interactionResultType.isInstance(baseInteractionResult)
                && result == toApiInteractionResult(baseInteractionResult)) {
            return baseInteractionResult;
        }
        if (result == EntityInteractionResult.SUCCESS) {
            return readInteractionResult("SUCCESS", "a");
        }
        if (result == EntityInteractionResult.CONSUME) {
            return readInteractionResult("CONSUME", "b");
        }
        if (result == EntityInteractionResult.FAIL) {
            return readInteractionResult("FAIL", "e");
        }
        return readInteractionResult("PASS", "d");
    }

    private static @NotNull Object readInteractionResult(@NotNull String namedField, @NotNull String obfuscatedField) {
        Class<?> interactionResultType = ReflectionSupport.requireClass("net.minecraft.world.InteractionResult");
        return ReflectionSupport.readField(
                ReflectionSupport.requireField(interactionResultType, namedField, obfuscatedField),
                null
        );
    }

    private static @Nullable Object resolveCurrentEquipmentItem(
            @NotNull LifecycleAwareNativeEntity nativeEntity,
            @NotNull Object equipmentSlot
    ) {
        Method getItemBySlotMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"getItemBySlot"},
                equipmentSlot.getClass()
        );
        if (getItemBySlotMethod == null) {
            return null;
        }
        return ReflectionSupport.invoke(getItemBySlotMethod, nativeEntity, equipmentSlot);
    }

    private static @Nullable ItemStack toBukkitItem(@Nullable Object nativeItem) {
        if (nativeItem == null) {
            return null;
        }
        Class<?> craftItemStackType = ReflectionSupport.requireClass("org.bukkit.craftbukkit.inventory.CraftItemStack");
        Method asBukkitCopyMethod = ReflectionSupport.requireNamedMethod(
                craftItemStackType,
                new String[]{"asBukkitCopy"},
                nativeItem.getClass()
        );
        return (ItemStack) ReflectionSupport.invoke(asBukkitCopyMethod, null, nativeItem);
    }

    private static @Nullable Object toNmsItem(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        Class<?> craftItemStackType = ReflectionSupport.requireClass("org.bukkit.craftbukkit.inventory.CraftItemStack");
        Method asNmsCopyMethod = ReflectionSupport.requireNamedMethod(
                craftItemStackType,
                new String[]{"asNMSCopy"},
                ItemStack.class
        );
        return ReflectionSupport.invoke(asNmsCopyMethod, null, itemStack);
    }

    private static @NotNull EntityEquipmentSlot toApiEquipmentSlot(@NotNull Object equipmentSlot) {
        String name = ((Enum<?>) equipmentSlot).name();
        if ("MAINHAND".equals(name)) {
            return EntityEquipmentSlot.MAIN_HAND;
        }
        if ("OFFHAND".equals(name)) {
            return EntityEquipmentSlot.OFF_HAND;
        }
        return EntityEquipmentSlot.valueOf(name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @NotNull Object toNmsEquipmentSlot(@NotNull EntityEquipmentSlot slot) {
        Class<?> equipmentSlotType = ReflectionSupport.requireClass("net.minecraft.world.entity.EquipmentSlot");
        String name = slot == EntityEquipmentSlot.MAIN_HAND
                ? "MAINHAND"
                : slot == EntityEquipmentSlot.OFF_HAND ? "OFFHAND" : slot.name();
        return Enum.valueOf((Class) equipmentSlotType, name);
    }

    private static @Nullable Class<?> findClass(@NotNull String... candidateNames) {
        try {
            return ReflectionSupport.requireClass(candidateNames);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }
}
