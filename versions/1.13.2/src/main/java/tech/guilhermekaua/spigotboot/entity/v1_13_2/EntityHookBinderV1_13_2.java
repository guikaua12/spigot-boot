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
package tech.guilhermekaua.spigotboot.entity.v1_13_2;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.EntityCollideContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityDamageContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityDieContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityEquipmentSlot;
import tech.guilhermekaua.spigotboot.entity.api.EntityInteractContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityInteractionHand;
import tech.guilhermekaua.spigotboot.entity.api.EntityInteractionResult;
import tech.guilhermekaua.spigotboot.entity.api.EntityInventoryChangeContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityMoveContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityPositionPassengerContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityPushContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

final class EntityHookBinderV1_13_2 implements NativeHookBinder<Entity> {
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
            final Object[] rawArguments = requireArguments(arguments, 4);
            controlledEntity.dispatchMove(
                    ((Double) rawArguments[1]).doubleValue(),
                    ((Double) rawArguments[2]).doubleValue(),
                    ((Double) rawArguments[3]).doubleValue(),
                    new ContextualBaseInvoker<EntityMoveContext<Entity>, Void>() {
                        @Override
                        public Void invoke(@NotNull EntityMoveContext<Entity> context) {
                            nativeEntity.spigotBootInvokeBase(
                                    HOOK_MOVE,
                                    new Object[]{
                                            rawArguments[0],
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
            return toNmsInteractionResult(result, baseInteractionResultHolder[0], rawArguments[1]);
        }

        if (HOOK_DIE.equals(hookName)) {
            final Object[] rawArguments = requireArguments(arguments, 0, 1);
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
            requireArguments(arguments, 0);
            controlledEntity.dispatchRemove(new ContextualBaseInvoker<EntityRemoveContext<Entity>, Void>() {
                @Override
                public Void invoke(@NotNull EntityRemoveContext<Entity> context) {
                    nativeEntity.spigotBootInvokeBase(HOOK_REMOVE, EMPTY_ARGUMENTS);
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
            final Object[] rawArguments = requireArguments(arguments, 0, 1);
            Object passengerHandle = rawArguments.length == 0 ? resolveLegacyPassenger(nativeEntity) : rawArguments[0];
            if (passengerHandle == null) {
                return null;
            }
            controlledEntity.dispatchPositionPassenger(
                    resolveBukkitEntity(passengerHandle),
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
                                                toNmsEquipmentSlot(context.slot(), nativeSlot),
                                                toNmsItem(context.previousItem(), rawArguments[1]),
                                                toNmsItem(context.newItem(), rawArguments[rawArguments.length - 1])
                                        }
                                );
                                return null;
                            }

                            nativeEntity.spigotBootInvokeBase(
                                    HOOK_INVENTORY_CHANGE,
                                    new Object[]{
                                            toNmsEquipmentSlot(context.slot(), nativeSlot),
                                            toNmsItem(context.newItem(), rawArguments[rawArguments.length - 1])
                                    }
                            );
                            return null;
                        }
                    }
            );
            return null;
        }

        throw new IllegalArgumentException("Unknown 1.13.2 Entity hook: " + hookName);
    }

    private @NotNull Collection<GeneratedNativeHookSpec> resolveHookSpecs(@NotNull Class<?> nativeType) {
        List<GeneratedNativeHookSpec> hookSpecs = new ArrayList<GeneratedNativeHookSpec>();
        addHookSpec(hookSpecs, HOOK_TICK, ReflectionSupport.findNamedMethod(nativeType, new String[]{"movementTick", "tick", "B_"}));

        Class<?> moveType = findRelativeClass(nativeType, "EnumMoveType");
        if (moveType != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_MOVE,
                    ReflectionSupport.findNamedMethod(
                            nativeType,
                            new String[]{"move", "a"},
                            moveType,
                            double.class,
                            double.class,
                            double.class
                    )
            );
        }

        addHookSpec(
                hookSpecs,
                HOOK_PUSH,
                ReflectionSupport.findNamedMethod(
                        nativeType,
                        new String[]{"push", "g", "i", "f"},
                        double.class,
                        double.class,
                        double.class
                )
        );

        Class<?> damageSourceType = findRelativeClass(nativeType, "DamageSource");
        if (damageSourceType != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_DAMAGE,
                    ReflectionSupport.findNamedMethod(
                            nativeType,
                            new String[]{"damageEntity", "hurt", "a"},
                            damageSourceType,
                            float.class
                    )
            );

            Method dieMethod = ReflectionSupport.findNamedMethod(nativeType, new String[]{"die"}, damageSourceType);
            if (dieMethod == null) {
                dieMethod = ReflectionSupport.findNamedMethod(nativeType, new String[]{"die"});
            }
            addHookSpec(hookSpecs, HOOK_DIE, dieMethod);
        }

        Class<?> playerType = findRelativeClass(nativeType, "EntityHuman", "Player");
        Class<?> interactionHandType = findRelativeClass(nativeType, "EnumHand", "InteractionHand");
        if (playerType != null && interactionHandType != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_INTERACT,
                    ReflectionSupport.findNamedMethod(
                            nativeType,
                            new String[]{"a", "interact", "mobInteract"},
                            playerType,
                            interactionHandType
                    )
            );
        }

        addHookSpec(hookSpecs, HOOK_REMOVE, ReflectionSupport.findNamedMethod(nativeType, new String[]{"remove", "discard"}));

        Class<?> entityType = findRelativeClass(nativeType, "Entity");
        if (entityType != null) {
            addHookSpec(
                    hookSpecs,
                    HOOK_COLLIDE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"collide", "push", "i", "a"}, entityType)
            );

            Method positionPassengerMethod = ReflectionSupport.findNamedMethod(
                    nativeType,
                    new String[]{"positionRider", "k"},
                    entityType
            );
            if (positionPassengerMethod == null) {
                positionPassengerMethod = ReflectionSupport.findNamedMethod(nativeType, new String[]{"positionRider", "k"});
            }
            addHookSpec(hookSpecs, HOOK_POSITION_PASSENGER, positionPassengerMethod);
        }

        Class<?> equipmentSlotType = findRelativeClass(nativeType, "EnumItemSlot", "EquipmentSlot");
        Class<?> itemStackType = findRelativeClass(nativeType, "ItemStack");
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
                        new String[]{"setSlot", "setItemSlot", "a"},
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

    private static @NotNull Entity resolveBukkitEntity(@NotNull Object nativeEntity) {
        Method getBukkitEntityMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"getBukkitEntity"}
        );
        if (getBukkitEntityMethod != null) {
            return (Entity) ReflectionSupport.invoke(getBukkitEntityMethod, nativeEntity);
        }

        Field bukkitEntityField = ReflectionSupport.findField(nativeEntity.getClass(), "bukkitEntity");
        if (bukkitEntityField != null) {
            Object wrapper = ReflectionSupport.readField(bukkitEntityField, nativeEntity);
            if (wrapper instanceof Entity) {
                return (Entity) wrapper;
            }
        }

        throw new IllegalStateException(
                "Could not resolve a Bukkit wrapper from native entity type '" + nativeEntity.getClass().getName() + "'."
        );
    }

    private static @NotNull EntityInteractionHand toApiHand(@NotNull Object hand) {
        String name = ((Enum<?>) hand).name();
        return "OFF_HAND".equals(name) || "OFFHAND".equals(name)
                ? EntityInteractionHand.OFF_HAND
                : EntityInteractionHand.MAIN_HAND;
    }

    private static @NotNull EntityInteractionResult toApiInteractionResult(@Nullable Object interactionResult) {
        if (interactionResult == null) {
            return EntityInteractionResult.PASS;
        }
        if (interactionResult instanceof Boolean) {
            return ((Boolean) interactionResult).booleanValue()
                    ? EntityInteractionResult.SUCCESS
                    : EntityInteractionResult.PASS;
        }
        if (interactionResult instanceof Enum) {
            String name = ((Enum<?>) interactionResult).name();
            if ("FAIL".equals(name) || "e".equals(name)) {
                return EntityInteractionResult.FAIL;
            }
            if ("PASS".equals(name) || "d".equals(name)) {
                return EntityInteractionResult.PASS;
            }
            if ("CONSUME".equals(name)
                    || "CONSUME_PARTIAL".equals(name)
                    || "b".equals(name)
                    || "c".equals(name)) {
                return EntityInteractionResult.CONSUME;
            }
            if ("SUCCESS".equals(name) || "a".equals(name)) {
                return EntityInteractionResult.SUCCESS;
            }
        }
        return EntityInteractionResult.PASS;
    }

    private static @NotNull Object toNmsInteractionResult(
            @NotNull EntityInteractionResult result,
            @Nullable Object baseInteractionResult,
            @NotNull Object interactionHand
    ) {
        if (baseInteractionResult instanceof Boolean) {
            return Boolean.valueOf(result == EntityInteractionResult.SUCCESS || result == EntityInteractionResult.CONSUME);
        }

        if (baseInteractionResult != null && result == toApiInteractionResult(baseInteractionResult)) {
            return baseInteractionResult;
        }

        Class<?> interactionResultType = baseInteractionResult != null
                ? baseInteractionResult.getClass()
                : findRelativeClass(interactionHand.getClass(), "EnumInteractionResult", "InteractionResult");
        if (interactionResultType == null || !interactionResultType.isEnum()) {
            throw new IllegalStateException("Could not resolve the native interaction result enum for 1.13.2.");
        }

        if (result == EntityInteractionResult.SUCCESS) {
            return requireEnumConstant(interactionResultType, "SUCCESS", "a");
        }
        if (result == EntityInteractionResult.CONSUME) {
            Object consume = findEnumConstant(interactionResultType, "CONSUME", "b", "CONSUME_PARTIAL", "c");
            return consume != null ? consume : requireEnumConstant(interactionResultType, "SUCCESS", "a");
        }
        if (result == EntityInteractionResult.FAIL) {
            return requireEnumConstant(interactionResultType, "FAIL", "e");
        }
        return requireEnumConstant(interactionResultType, "PASS", "d");
    }

    private static @Nullable Object resolveCurrentEquipmentItem(
            @NotNull LifecycleAwareNativeEntity nativeEntity,
            @NotNull Object equipmentSlot
    ) {
        Method getEquipmentMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"getEquipment", "getItemSlot", "getItemBySlot", "c"},
                equipmentSlot.getClass()
        );
        if (getEquipmentMethod == null) {
            return null;
        }
        return ReflectionSupport.invoke(getEquipmentMethod, nativeEntity, equipmentSlot);
    }

    private static @Nullable ItemStack toBukkitItem(@Nullable Object nativeItem) {
        if (nativeItem == null) {
            return null;
        }
        Class<?> craftItemStackType = resolveCraftItemStackClass(nativeItem.getClass());
        Method asBukkitCopyMethod = ReflectionSupport.requireNamedMethod(
                craftItemStackType,
                new String[]{"asBukkitCopy"},
                nativeItem.getClass()
        );
        return (ItemStack) ReflectionSupport.invoke(asBukkitCopyMethod, null, nativeItem);
    }

    private static @Nullable Object toNmsItem(@Nullable ItemStack itemStack, @Nullable Object nativeAnchor) {
        if (itemStack == null) {
            return null;
        }
        Class<?> craftItemStackType = resolveCraftItemStackClass(nativeAnchor == null ? null : nativeAnchor.getClass());
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

    private static @NotNull Object toNmsEquipmentSlot(@NotNull EntityEquipmentSlot slot, @NotNull Object nativeSlot) {
        Object resolvedSlot = findEnumConstant(
                nativeSlot.getClass(),
                slot == EntityEquipmentSlot.MAIN_HAND
                        ? "MAINHAND"
                        : slot == EntityEquipmentSlot.OFF_HAND ? "OFFHAND" : slot.name(),
                slot.name()
        );
        if (resolvedSlot != null) {
            return resolvedSlot;
        }
        throw new IllegalStateException(
                "Could not resolve the native equipment slot for '" + slot + "' from '"
                        + nativeSlot.getClass().getName() + "'."
        );
    }

    private static @Nullable Object resolveLegacyPassenger(@NotNull LifecycleAwareNativeEntity nativeEntity) {
        Field passengerField = ReflectionSupport.findField(nativeEntity.getClass(), "passenger");
        if (passengerField != null) {
            return ReflectionSupport.readField(passengerField, nativeEntity);
        }

        Field passengersField = ReflectionSupport.findField(nativeEntity.getClass(), "passengers");
        if (passengersField == null) {
            return null;
        }

        Object passengers = ReflectionSupport.readField(passengersField, nativeEntity);
        if (!(passengers instanceof List) || ((List<?>) passengers).isEmpty()) {
            return null;
        }
        return ((List<?>) passengers).get(0);
    }

    private static @NotNull Class<?> resolveCraftItemStackClass(@Nullable Class<?> nativeAnchorType) {
        try {
            return ReflectionSupport.requireClass("org.bukkit.craftbukkit.inventory.CraftItemStack");
        } catch (IllegalStateException ignored) {
        }

        if (nativeAnchorType != null) {
            Package declaredPackage = nativeAnchorType.getPackage();
            if (declaredPackage != null) {
                String packageName = declaredPackage.getName();
                String prefix = "net.minecraft.server.";
                if (packageName.startsWith(prefix)) {
                    String versionToken = packageName.substring(prefix.length());
                    return ReflectionSupport.requireClass(
                            "org.bukkit.craftbukkit." + versionToken + ".inventory.CraftItemStack"
                    );
                }
            }
        }

        throw new IllegalStateException("Could not resolve CraftItemStack for the 1.13.2 native hook bridge.");
    }

    private static @Nullable Class<?> findRelativeClass(@NotNull Class<?> anchorType, @NotNull String... binarySimpleNames) {
        Package declaredPackage = anchorType.getPackage();
        if (declaredPackage == null) {
            return null;
        }
        for (String binarySimpleName : binarySimpleNames) {
            try {
                return ReflectionSupport.requireClass(declaredPackage.getName() + "." + binarySimpleName);
            } catch (IllegalStateException ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable Object findEnumConstant(@NotNull Class<?> enumType, @NotNull String... candidateNames) {
        for (String candidateName : candidateNames) {
            try {
                return Enum.valueOf((Class) enumType, candidateName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static @NotNull Object requireEnumConstant(@NotNull Class<?> enumType, @NotNull String... candidateNames) {
        Object constant = findEnumConstant(enumType, candidateNames);
        if (constant != null) {
            return constant;
        }
        throw new IllegalStateException(
                "Could not resolve any of " + java.util.Arrays.toString(candidateNames)
                        + " from native enum type '" + enumType.getName() + "'."
        );
    }
}
