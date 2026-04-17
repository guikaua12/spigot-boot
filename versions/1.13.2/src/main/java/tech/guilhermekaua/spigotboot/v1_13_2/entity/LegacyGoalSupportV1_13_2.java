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
package tech.guilhermekaua.spigotboot.v1_13_2.entity;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationBatch;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class LegacyGoalSupportV1_13_2 {
    private static final Set<VanillaGoalKey> SUPPORTED_VANILLA_GOAL_KEYS = EnumSet.of(
            VanillaGoalKey.FLOAT,
            VanillaGoalKey.MELEE_ATTACK,
            VanillaGoalKey.RANDOM_STROLL_LAND,
            VanillaGoalKey.LOOK_AT_PLAYER,
            VanillaGoalKey.RANDOM_LOOK_AROUND,
            VanillaGoalKey.HURT_BY_TARGET,
            VanillaGoalKey.NEAREST_ATTACKABLE_TARGET
    );
    private static final VersionGoalSupportMetadata GOAL_SUPPORT_METADATA = new VersionGoalSupportMetadata(
            SUPPORTED_VANILLA_GOAL_KEYS,
            true,
            true,
            true
    );

    private static final String NMS_PACKAGE = "net.minecraft.server.v1_13_R2.";
    private static final String ENTITY_HUMAN_CLASS_NAME = NMS_PACKAGE + "EntityHuman";
    private static final String PATHFINDER_GOAL_CLASS_NAME = NMS_PACKAGE + "PathfinderGoal";
    private static final String PATHFINDER_GOAL_FLOAT_CLASS_NAME = NMS_PACKAGE + "PathfinderGoalFloat";
    private static final String PATHFINDER_GOAL_MELEE_ATTACK_CLASS_NAME = NMS_PACKAGE + "PathfinderGoalMeleeAttack";
    private static final String PATHFINDER_GOAL_RANDOM_STROLL_LAND_CLASS_NAME =
            NMS_PACKAGE + "PathfinderGoalRandomStrollLand";
    private static final String PATHFINDER_GOAL_LOOK_AT_PLAYER_CLASS_NAME = NMS_PACKAGE + "PathfinderGoalLookAtPlayer";
    private static final String PATHFINDER_GOAL_RANDOM_LOOK_AROUND_CLASS_NAME =
            NMS_PACKAGE + "PathfinderGoalRandomLookaround";
    private static final String PATHFINDER_GOAL_HURT_BY_TARGET_CLASS_NAME = NMS_PACKAGE + "PathfinderGoalHurtByTarget";
    private static final String PATHFINDER_GOAL_NEAREST_ATTACKABLE_TARGET_CLASS_NAME =
            NMS_PACKAGE + "PathfinderGoalNearestAttackableTarget";

    private static final String CUSTOM_GOAL_CLASS_NAME =
            LegacyGoalSupportV1_13_2.class.getPackage().getName() + ".SpigotBootLegacyCustomGoalV1_13_2";
    private static final String CUSTOM_GOAL_KEY_FIELD_NAME = "spigotBoot$key";
    private static final String CUSTOM_GOAL_KEY_METHOD_NAME = "spigotBootCustomGoalKey";

    private static final double DEFAULT_MOVEMENT_SPEED = 1.0D;
    private static final float DEFAULT_LOOK_DISTANCE = 8.0F;

    private static volatile Class<?> customGoalType;

    private LegacyGoalSupportV1_13_2() {
    }

    static @NotNull VersionGoalSupportMetadata goalSupportMetadata() {
        return GOAL_SUPPORT_METADATA;
    }

    static <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createSpawnExecutor(
            @NotNull EntityTemplate<T> template
    ) {
        EntityTemplate<T> resolvedTemplate = Objects.requireNonNull(template, "template cannot be null");
        return new LegacyGoalMutationExecutor<T>(resolvedTemplate.goalProfile());
    }

    static <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createAttachedExecutor(@NotNull T entity) {
        T resolvedEntity = Objects.requireNonNull(entity, "entity cannot be null");
        return new LegacyGoalMutationExecutor<T>(
                snapshotManagedGoals(resolveNativeHandle(resolvedEntity), entityType(resolvedEntity))
        );
    }

    private static final class LegacyGoalMutationExecutor<T extends Entity> implements RuntimeGoalMutationExecutor<T> {
        private final GoalProfile<T> initialManagedGoals;

        private LegacyGoalMutationExecutor(@NotNull GoalProfile<T> initialManagedGoals) {
            this.initialManagedGoals = Objects.requireNonNull(initialManagedGoals, "initialManagedGoals cannot be null");
        }

        @Override
        public @NotNull GoalProfile<T> initialManagedGoals() {
            return initialManagedGoals;
        }

        @Override
        public void execute(@NotNull RuntimeGoalMutationBatch<T> batch) {
            RuntimeGoalMutationBatch<T> resolvedBatch = Objects.requireNonNull(batch, "batch cannot be null");
            syncManagedGoals(
                    resolveNativeHandle(resolvedBatch.controlledEntity().bukkitEntity()),
                    resolvedBatch.managedGoals()
            );
        }
    }

    private static void syncManagedGoals(@NotNull Object nativeHandle, @NotNull GoalProfile<?> managedGoals) {
        Objects.requireNonNull(nativeHandle, "nativeHandle cannot be null");
        GoalProfile<?> resolvedManagedGoals = Objects.requireNonNull(managedGoals, "managedGoals cannot be null");
        for (GoalSelectorType selectorType : GoalSelectorType.values()) {
            syncSelector(nativeHandle, selectorType, resolvedManagedGoals);
        }
    }

    private static void syncSelector(
            @NotNull Object nativeHandle,
            @NotNull GoalSelectorType selectorType,
            @NotNull GoalProfile<?> managedGoals
    ) {
        Object selector = resolveSelector(nativeHandle, selectorType);
        List<ObservedGoalEntry> observedEntries = observeManagedEntries(selector);
        Map<VanillaGoalKey, VanillaGoalSpec> desiredVanillaGoals = desiredVanillaGoals(managedGoals, selectorType);
        Map<CustomGoalKey, CustomGoalSpec> desiredCustomGoals = desiredCustomGoals(managedGoals, selectorType);
        Set<VanillaGoalKey> satisfiedVanillaGoals = EnumSet.noneOf(VanillaGoalKey.class);
        Set<CustomGoalKey> satisfiedCustomGoals = new LinkedHashSet<CustomGoalKey>();

        for (ObservedGoalEntry observedEntry : observedEntries) {
            if (observedEntry.vanillaKey() != null) {
                VanillaGoalSpec desiredSpec = desiredVanillaGoals.get(observedEntry.vanillaKey());
                if (desiredSpec != null
                        && desiredSpec.priority() == observedEntry.priority()
                        && !satisfiedVanillaGoals.contains(observedEntry.vanillaKey())) {
                    satisfiedVanillaGoals.add(observedEntry.vanillaKey());
                    continue;
                }
            }

            if (observedEntry.customKey() != null) {
                CustomGoalSpec desiredSpec = desiredCustomGoals.get(observedEntry.customKey());
                if (desiredSpec != null
                        && desiredSpec.priority() == observedEntry.priority()
                        && !satisfiedCustomGoals.contains(observedEntry.customKey())) {
                    satisfiedCustomGoals.add(observedEntry.customKey());
                    continue;
                }
            }

            removeGoal(selector, observedEntry.goal());
        }

        for (VanillaGoalSpec goalSpec : desiredVanillaGoals.values()) {
            if (!satisfiedVanillaGoals.contains(goalSpec.key())) {
                addGoal(selector, goalSpec.priority(), createVanillaGoal(nativeHandle, goalSpec.key()));
            }
        }

        for (CustomGoalSpec goalSpec : desiredCustomGoals.values()) {
            if (!satisfiedCustomGoals.contains(goalSpec.key())) {
                addGoal(selector, goalSpec.priority(), createCustomGoal(goalSpec.key()));
            }
        }
    }

    private static @NotNull Map<VanillaGoalKey, VanillaGoalSpec> desiredVanillaGoals(
            @NotNull GoalProfile<?> managedGoals,
            @NotNull GoalSelectorType selectorType
    ) {
        Map<VanillaGoalKey, VanillaGoalSpec> desiredGoals = new LinkedHashMap<VanillaGoalKey, VanillaGoalSpec>();
        for (VanillaGoalSpec goalSpec : managedGoals.vanillaGoals(selectorType)) {
            desiredGoals.put(goalSpec.key(), goalSpec);
        }
        return desiredGoals;
    }

    private static @NotNull Map<CustomGoalKey, CustomGoalSpec> desiredCustomGoals(
            @NotNull GoalProfile<?> managedGoals,
            @NotNull GoalSelectorType selectorType
    ) {
        Map<CustomGoalKey, CustomGoalSpec> desiredGoals = new LinkedHashMap<CustomGoalKey, CustomGoalSpec>();
        for (CustomGoalSpec goalSpec : managedGoals.customGoals(selectorType)) {
            desiredGoals.put(goalSpec.key(), goalSpec);
        }
        return desiredGoals;
    }

    private static @NotNull Object createVanillaGoal(@NotNull Object nativeHandle, @NotNull VanillaGoalKey key) {
        Objects.requireNonNull(nativeHandle, "nativeHandle cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        switch (key) {
            case FLOAT:
                return instantiate(
                        PATHFINDER_GOAL_FLOAT_CLASS_NAME,
                        new Object[]{nativeHandle},
                        nativeHandle.getClass()
                );
            case MELEE_ATTACK:
                return instantiate(
                        PATHFINDER_GOAL_MELEE_ATTACK_CLASS_NAME,
                        new Object[]{nativeHandle, Double.valueOf(DEFAULT_MOVEMENT_SPEED), Boolean.FALSE},
                        nativeHandle.getClass(),
                        double.class,
                        boolean.class
                );
            case RANDOM_STROLL_LAND:
                return instantiate(
                        PATHFINDER_GOAL_RANDOM_STROLL_LAND_CLASS_NAME,
                        new Object[]{nativeHandle, Double.valueOf(DEFAULT_MOVEMENT_SPEED)},
                        nativeHandle.getClass(),
                        double.class
                );
            case LOOK_AT_PLAYER:
                return instantiate(
                        PATHFINDER_GOAL_LOOK_AT_PLAYER_CLASS_NAME,
                        new Object[]{nativeHandle, resolveClass(ENTITY_HUMAN_CLASS_NAME), Float.valueOf(DEFAULT_LOOK_DISTANCE)},
                        nativeHandle.getClass(),
                        Class.class,
                        float.class
                );
            case RANDOM_LOOK_AROUND:
                return instantiate(
                        PATHFINDER_GOAL_RANDOM_LOOK_AROUND_CLASS_NAME,
                        new Object[]{nativeHandle},
                        nativeHandle.getClass()
                );
            case HURT_BY_TARGET:
                return instantiate(
                        PATHFINDER_GOAL_HURT_BY_TARGET_CLASS_NAME,
                        new Object[]{nativeHandle, Boolean.TRUE, new Class[0]},
                        nativeHandle.getClass(),
                        boolean.class,
                        Class[].class
                );
            case NEAREST_ATTACKABLE_TARGET:
                return instantiate(
                        PATHFINDER_GOAL_NEAREST_ATTACKABLE_TARGET_CLASS_NAME,
                        new Object[]{nativeHandle, resolveClass(ENTITY_HUMAN_CLASS_NAME), Boolean.TRUE},
                        nativeHandle.getClass(),
                        Class.class,
                        boolean.class
                );
            default:
                throw new IllegalArgumentException("Unsupported legacy vanilla goal key: " + key + '.');
        }
    }

    private static @NotNull Object createCustomGoal(@NotNull CustomGoalKey key) {
        Objects.requireNonNull(key, "key cannot be null");
        try {
            Constructor<?> constructor = resolveCustomGoalType().getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(key.toString());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not instantiate the generated legacy custom goal wrapper.", exception);
        }
    }

    private static @NotNull Class<?> resolveCustomGoalType() {
        Class<?> resolvedCustomGoalType = customGoalType;
        if (resolvedCustomGoalType != null) {
            return resolvedCustomGoalType;
        }

        synchronized (LegacyGoalSupportV1_13_2.class) {
            if (customGoalType != null) {
                return customGoalType;
            }

            try {
                customGoalType = Class.forName(
                        CUSTOM_GOAL_CLASS_NAME,
                        false,
                        LegacyGoalSupportV1_13_2.class.getClassLoader()
                );
                return customGoalType;
            } catch (ClassNotFoundException ignored) {
            }

            try {
                customGoalType = MethodHandles.lookup().defineClass(generateCustomGoalClassBytes(
                        CUSTOM_GOAL_CLASS_NAME,
                        resolveClass(PATHFINDER_GOAL_CLASS_NAME)
                ));
                return customGoalType;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not define the generated legacy custom goal wrapper.", exception);
            }
        }
    }

    private static @NotNull byte[] generateCustomGoalClassBytes(
            @NotNull String className,
            @NotNull Class<?> superclass
    ) {
        String internalName = className.replace('.', '/');
        String superclassInternalName = superclass.getName().replace('.', '/');
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            DataOutputStream output = new DataOutputStream(outputStream);
            output.writeInt(0xCAFEBABE);
            output.writeShort(0);
            output.writeShort(49);
            output.writeShort(19);
            writeUtf8(output, internalName);
            writeClass(output, 1);
            writeUtf8(output, superclassInternalName);
            writeClass(output, 3);
            writeUtf8(output, "<init>");
            writeUtf8(output, "()V");
            writeNameAndType(output, 5, 6);
            writeMethodRef(output, 4, 7);
            writeUtf8(output, CUSTOM_GOAL_KEY_FIELD_NAME);
            writeUtf8(output, "Ljava/lang/String;");
            writeNameAndType(output, 9, 10);
            writeFieldRef(output, 2, 11);
            writeUtf8(output, "Code");
            writeUtf8(output, "(Ljava/lang/String;)V");
            writeUtf8(output, "a");
            writeUtf8(output, "()Z");
            writeUtf8(output, CUSTOM_GOAL_KEY_METHOD_NAME);
            writeUtf8(output, "()Ljava/lang/String;");
            output.writeShort(0x0030);
            output.writeShort(2);
            output.writeShort(4);
            output.writeShort(0);
            output.writeShort(1);
            output.writeShort(0x0012);
            output.writeShort(9);
            output.writeShort(10);
            output.writeShort(0);
            output.writeShort(3);
            writeConstructor(output);
            writeCanUseMethod(output);
            writeCustomGoalKeyAccessor(output);
            output.writeShort(0);
            output.flush();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate the legacy custom goal wrapper bytes.", exception);
        }
    }

    private static void writeConstructor(@NotNull DataOutputStream output) throws IOException {
        output.writeShort(0x0001);
        output.writeShort(5);
        output.writeShort(14);
        output.writeShort(1);
        output.writeShort(13);
        output.writeInt(22);
        output.writeShort(2);
        output.writeShort(2);
        output.writeInt(10);
        output.writeByte(0x2A);
        output.writeByte(0xB7);
        output.writeShort(8);
        output.writeByte(0x2A);
        output.writeByte(0x2B);
        output.writeByte(0xB5);
        output.writeShort(12);
        output.writeByte(0xB1);
        output.writeShort(0);
        output.writeShort(0);
    }

    private static void writeCanUseMethod(@NotNull DataOutputStream output) throws IOException {
        output.writeShort(0x0001);
        output.writeShort(15);
        output.writeShort(16);
        output.writeShort(1);
        output.writeShort(13);
        output.writeInt(14);
        output.writeShort(1);
        output.writeShort(1);
        output.writeInt(2);
        output.writeByte(0x03);
        output.writeByte(0xAC);
        output.writeShort(0);
        output.writeShort(0);
    }

    private static void writeCustomGoalKeyAccessor(@NotNull DataOutputStream output) throws IOException {
        output.writeShort(0x0001);
        output.writeShort(17);
        output.writeShort(18);
        output.writeShort(1);
        output.writeShort(13);
        output.writeInt(17);
        output.writeShort(1);
        output.writeShort(1);
        output.writeInt(5);
        output.writeByte(0x2A);
        output.writeByte(0xB4);
        output.writeShort(12);
        output.writeByte(0xB0);
        output.writeShort(0);
        output.writeShort(0);
    }

    private static void writeUtf8(@NotNull DataOutputStream output, @NotNull String value) throws IOException {
        output.writeByte(1);
        output.writeUTF(value);
    }

    private static void writeClass(@NotNull DataOutputStream output, int nameIndex) throws IOException {
        output.writeByte(7);
        output.writeShort(nameIndex);
    }

    private static void writeNameAndType(@NotNull DataOutputStream output, int nameIndex, int descriptorIndex)
            throws IOException {
        output.writeByte(12);
        output.writeShort(nameIndex);
        output.writeShort(descriptorIndex);
    }

    private static void writeMethodRef(@NotNull DataOutputStream output, int classIndex, int nameAndTypeIndex)
            throws IOException {
        output.writeByte(10);
        output.writeShort(classIndex);
        output.writeShort(nameAndTypeIndex);
    }

    private static void writeFieldRef(@NotNull DataOutputStream output, int classIndex, int nameAndTypeIndex)
            throws IOException {
        output.writeByte(9);
        output.writeShort(classIndex);
        output.writeShort(nameAndTypeIndex);
    }

    private static @NotNull Object instantiate(
            @NotNull String className,
            @NotNull Object[] arguments,
            @NotNull Class<?>... argumentTypes
    ) {
        Class<?> type = resolveClass(className);
        Constructor<?> constructor = requireCompatibleConstructor(type, argumentTypes);
        return ReflectionSupport.instantiate(constructor, arguments);
    }

    private static @NotNull Constructor<?> requireCompatibleConstructor(
            @NotNull Class<?> type,
            @NotNull Class<?>... argumentTypes
    ) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != argumentTypes.length) {
                continue;
            }

            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!wrap(parameterTypes[index]).isAssignableFrom(wrap(argumentTypes[index]))) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }

            constructor.setAccessible(true);
            return constructor;
        }

        throw new IllegalStateException(
                "Could not resolve a compatible constructor on '" + type.getName() + "'."
        );
    }

    private static @NotNull Class<?> wrap(@NotNull Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        throw new IllegalStateException("Unsupported primitive type '" + type.getName() + "'.");
    }

    private static @NotNull List<ObservedGoalEntry> observeManagedEntries(@NotNull Object selector) {
        Collection<?> registeredEntries = registeredEntries(selector);
        if (registeredEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<ObservedGoalEntry> observedEntries = new ArrayList<ObservedGoalEntry>();
        for (Object entry : new ArrayList<Object>(registeredEntries)) {
            ObservedGoalEntry observedEntry = observeEntry(entry);
            if (observedEntry != null) {
                observedEntries.add(observedEntry);
            }
        }
        return observedEntries;
    }

    private static @Nullable ObservedGoalEntry observeEntry(@Nullable Object entry) {
        if (entry == null) {
            return null;
        }

        Field goalField = ReflectionSupport.findField(entry.getClass(), "a");
        Field priorityField = ReflectionSupport.findField(entry.getClass(), "b");
        if (goalField == null || priorityField == null) {
            return null;
        }

        Object goal = ReflectionSupport.readField(goalField, entry);
        if (goal == null) {
            return null;
        }

        Number priority = (Number) ReflectionSupport.readField(priorityField, entry);
        if (priority == null) {
            return null;
        }

        CustomGoalKey customKey = resolveCustomGoalKey(goal);
        if (customKey != null) {
            return new ObservedGoalEntry(goal, priority.intValue(), null, customKey);
        }

        VanillaGoalKey vanillaKey = resolveVanillaGoalKey(goal);
        if (vanillaKey == null) {
            return null;
        }

        return new ObservedGoalEntry(goal, priority.intValue(), vanillaKey, null);
    }

    private static @Nullable VanillaGoalKey resolveVanillaGoalKey(@NotNull Object goal) {
        Objects.requireNonNull(goal, "goal cannot be null");
        if (isInstance(goal, PATHFINDER_GOAL_FLOAT_CLASS_NAME)) {
            return VanillaGoalKey.FLOAT;
        }
        if (isInstance(goal, PATHFINDER_GOAL_MELEE_ATTACK_CLASS_NAME)) {
            return VanillaGoalKey.MELEE_ATTACK;
        }
        if (isInstance(goal, PATHFINDER_GOAL_RANDOM_STROLL_LAND_CLASS_NAME)) {
            return VanillaGoalKey.RANDOM_STROLL_LAND;
        }
        if (isInstance(goal, PATHFINDER_GOAL_LOOK_AT_PLAYER_CLASS_NAME)) {
            return VanillaGoalKey.LOOK_AT_PLAYER;
        }
        if (isInstance(goal, PATHFINDER_GOAL_RANDOM_LOOK_AROUND_CLASS_NAME)) {
            return VanillaGoalKey.RANDOM_LOOK_AROUND;
        }
        if (isInstance(goal, PATHFINDER_GOAL_HURT_BY_TARGET_CLASS_NAME)) {
            return VanillaGoalKey.HURT_BY_TARGET;
        }
        if (isInstance(goal, PATHFINDER_GOAL_NEAREST_ATTACKABLE_TARGET_CLASS_NAME)) {
            return VanillaGoalKey.NEAREST_ATTACKABLE_TARGET;
        }
        return null;
    }

    private static boolean isInstance(@NotNull Object value, @NotNull String className) {
        return resolveClass(className).isInstance(value);
    }

    private static @Nullable CustomGoalKey resolveCustomGoalKey(@NotNull Object goal) {
        Objects.requireNonNull(goal, "goal cannot be null");
        Method customGoalKeyMethod = ReflectionSupport.findNamedMethod(
                goal.getClass(),
                new String[]{CUSTOM_GOAL_KEY_METHOD_NAME}
        );
        if (customGoalKeyMethod == null) {
            return null;
        }

        Object customGoalKey = ReflectionSupport.invoke(customGoalKeyMethod, goal);
        if (!(customGoalKey instanceof String)) {
            return null;
        }

        try {
            return CustomGoalKey.parse((String) customGoalKey);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @NotNull Collection<?> registeredEntries(@NotNull Object selector) {
        Objects.requireNonNull(selector, "selector cannot be null");
        Field entriesField = ReflectionSupport.requireField(selector.getClass(), "b");
        Object entries = ReflectionSupport.readField(entriesField, selector);
        if (entries instanceof Collection) {
            return (Collection<?>) entries;
        }
        return Collections.emptyList();
    }

    private static void addGoal(@NotNull Object selector, int priority, @NotNull Object goal) {
        Objects.requireNonNull(selector, "selector cannot be null");
        Objects.requireNonNull(goal, "goal cannot be null");
        Method addMethod = ReflectionSupport.requireNamedMethod(
                selector.getClass(),
                new String[]{"a"},
                int.class,
                resolveClass(PATHFINDER_GOAL_CLASS_NAME)
        );
        ReflectionSupport.invoke(addMethod, selector, Integer.valueOf(priority), goal);
    }

    private static void removeGoal(@NotNull Object selector, @NotNull Object goal) {
        Objects.requireNonNull(selector, "selector cannot be null");
        Objects.requireNonNull(goal, "goal cannot be null");
        Method removeMethod = ReflectionSupport.requireNamedMethod(
                selector.getClass(),
                new String[]{"a"},
                resolveClass(PATHFINDER_GOAL_CLASS_NAME)
        );
        ReflectionSupport.invoke(removeMethod, selector, goal);
    }

    private static @NotNull Object resolveSelector(@NotNull Object nativeHandle, @NotNull GoalSelectorType selectorType) {
        Field selectorField = ReflectionSupport.requireField(
                nativeHandle.getClass(),
                selectorType == GoalSelectorType.NORMAL ? "goalSelector" : "targetSelector"
        );
        Object selector = ReflectionSupport.readField(selectorField, nativeHandle);
        if (selector == null) {
            throw new IllegalStateException(
                    "The legacy selector '" + selectorType.name() + "' was not available on '"
                            + nativeHandle.getClass().getName() + "'."
            );
        }
        return selector;
    }

    private static @NotNull Class<?> resolveClass(@NotNull String className) {
        return ReflectionSupport.requireClass(className);
    }

    private static @NotNull Object resolveNativeHandle(@NotNull Entity entity) {
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(entity.getClass(), new String[]{"getHandle"});
        Object nativeHandle = ReflectionSupport.invoke(getHandleMethod, entity);
        if (nativeHandle == null) {
            throw new IllegalStateException(
                    "The Bukkit entity '" + entity.getClass().getName() + "' did not expose a native handle instance."
            );
        }
        return nativeHandle;
    }

    private static <T extends Entity> @NotNull GoalProfile<T> snapshotManagedGoals(
            @NotNull Object nativeHandle,
            @NotNull Class<T> entityType
    ) {
        GoalProfile.Builder<T> builder = GoalProfile.builder(entityType);
        for (GoalSelectorType selectorType : GoalSelectorType.values()) {
            for (ObservedGoalEntry observedEntry : observeManagedEntries(resolveSelector(nativeHandle, selectorType))) {
                if (observedEntry.vanillaKey() != null) {
                    builder.addVanilla(selectorType, observedEntry.vanillaKey(), observedEntry.priority());
                    continue;
                }
                if (observedEntry.customKey() != null) {
                    builder.addCustom(selectorType, observedEntry.customKey(), observedEntry.priority());
                }
            }
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull Class<T> entityType(@NotNull T entity) {
        return (Class<T>) entity.getClass().asSubclass(Entity.class);
    }

    private static final class ObservedGoalEntry {
        private final Object goal;
        private final int priority;
        private final VanillaGoalKey vanillaKey;
        private final CustomGoalKey customKey;

        private ObservedGoalEntry(
                @NotNull Object goal,
                int priority,
                @Nullable VanillaGoalKey vanillaKey,
                @Nullable CustomGoalKey customKey
        ) {
            this.goal = goal;
            this.priority = priority;
            this.vanillaKey = vanillaKey;
            this.customKey = customKey;
        }

        private @NotNull Object goal() {
            return goal;
        }

        private int priority() {
            return priority;
        }

        private @Nullable VanillaGoalKey vanillaKey() {
            return vanillaKey;
        }

        private @Nullable CustomGoalKey customKey() {
            return customKey;
        }
    }
}
