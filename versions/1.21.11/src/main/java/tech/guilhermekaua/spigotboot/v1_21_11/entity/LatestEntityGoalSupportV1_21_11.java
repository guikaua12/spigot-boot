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
package tech.guilhermekaua.spigotboot.v1_21_11.entity;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalManager;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationBatch;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class LatestEntityGoalSupportV1_21_11 {
    private static final VersionGoalSupportMetadata METADATA = new VersionGoalSupportMetadata(
            EnumSet.allOf(VanillaGoalKey.class),
            true,
            true,
            true
    );

    private LatestEntityGoalSupportV1_21_11() {
    }

    static @NotNull VersionGoalSupportMetadata metadata() {
        return METADATA;
    }

    static <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createSpawnExecutor(
            @NotNull EntityTemplate<T> template
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        return new GoalMutationExecutorV1_21_11<T>(template.goalProfile(), true);
    }

    static <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createAttachedExecutor(
            @NotNull CustomEntityBaseType baseType,
            @NotNull T entity
    ) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        return new GoalMutationExecutorV1_21_11<T>(resolveAttachedManagedGoals(entity), false);
    }

    static void bindNativeHandle(@NotNull NativeEntityLifecycle<?> lifecycle, @NotNull Object nativeHandle) {
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        Objects.requireNonNull(nativeHandle, "nativeHandle cannot be null");

        ControlledEntity<?> controlledEntity = lifecycle.handle();
        if (controlledEntity == null) {
            return;
        }
        GoalManager<?> goalManager = controlledEntity.goalManager();
        Object executor = resolveNamedMember(goalManager, "executor");
        if (executor instanceof GoalMutationExecutorV1_21_11) {
            ((GoalMutationExecutorV1_21_11<?>) executor).bindNativeHandle(nativeHandle);
        }
    }

    private static <T extends Entity> @NotNull GoalProfile<T> resolveAttachedManagedGoals(@NotNull T entity) {
        GoalProfile.Builder<T> builder = GoalProfile.builder(resolveEntityType(entity));
        Object nativeHandle = resolveNativeHandle(entity);
        if (nativeHandle == null) {
            return builder.build();
        }
        for (GoalSelectorType selectorType : GoalSelectorType.values()) {
            for (Object entry : resolveSelectorEntries(nativeHandle, selectorType)) {
                ManagedGoalEntry managedGoalEntry = ManagedGoalEntry.resolve(entry);
                if (managedGoalEntry == null) {
                    continue;
                }
                if (managedGoalEntry.vanillaKeyOrNull() != null) {
                    builder.add(VanillaGoalSpec.of(selectorType, managedGoalEntry.vanillaKeyOrNull(), managedGoalEntry.priority()));
                    continue;
                }
                builder.add(CustomGoalSpec.of(selectorType, managedGoalEntry.customKeyOrNull(), managedGoalEntry.priority()));
            }
        }
        return builder.build();
    }

    private static void applyManagedGoalSnapshot(
            @NotNull Object nativeHandle,
            @NotNull GoalProfile<?> managedGoals
    ) {
        Objects.requireNonNull(nativeHandle, "nativeHandle cannot be null");
        Objects.requireNonNull(managedGoals, "managedGoals cannot be null");
        for (GoalSelectorType selectorType : GoalSelectorType.values()) {
            SelectorAccessor selectorAccessor = SelectorAccessor.resolve(nativeHandle, selectorType);
            selectorAccessor.removeManagedEntries();
            for (VanillaGoalSpec goalSpec : managedGoals.vanillaGoals(selectorType)) {
                selectorAccessor.add(goalSpec.priority(), selectorAccessor.createVanillaGoal(goalSpec));
            }
            for (CustomGoalSpec goalSpec : managedGoals.customGoals(selectorType)) {
                selectorAccessor.add(goalSpec.priority(), new ManagedCustomGoalBridge(goalSpec.key()));
            }
        }
    }

    private static @Nullable Object resolveNativeHandle(@NotNull Entity entity) {
        Method method = findNoArgMethod(entity.getClass(), "getHandle");
        return method == null ? null : invoke(method, entity);
    }

    private static @Nullable Object resolveSelector(@NotNull Object nativeHandle, @NotNull GoalSelectorType selectorType) {
        return resolveNamedMember(nativeHandle, selectorType == GoalSelectorType.NORMAL ? "goalSelector" : "targetSelector");
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Collection<Object> resolveSelectorEntries(
            @NotNull Object nativeHandle,
            @NotNull GoalSelectorType selectorType
    ) {
        Object selector = resolveSelector(nativeHandle, selectorType);
        if (selector == null) {
            return new ArrayList<Object>();
        }
        Object entries = resolveCollectionValue(selector, "getAvailableGoals", "getGoals", "availableGoals", "goals", "entries");
        if (entries instanceof Collection) {
            return (Collection<Object>) entries;
        }
        return new ArrayList<Object>();
    }

    private static @Nullable Object resolveCollectionValue(@NotNull Object instance, @NotNull String... candidates) {
        for (String candidate : candidates) {
            Method method = findNoArgMethod(instance.getClass(), candidate);
            if (method != null) {
                Object value = invoke(method, instance);
                if (value instanceof Collection) {
                    return value;
                }
            }
            Field field = findField(instance.getClass(), candidate);
            if (field != null) {
                Object value = read(field, instance);
                if (value instanceof Collection) {
                    return value;
                }
            }
        }
        Field collectionField = findCollectionField(instance.getClass());
        return collectionField == null ? null : read(collectionField, instance);
    }

    private static @Nullable Object resolveNamedMember(@NotNull Object instance, @NotNull String name) {
        Field field = findField(instance.getClass(), name);
        if (field != null) {
            return read(field, instance);
        }
        Method getter = findNoArgMethod(instance.getClass(), "get" + capitalize(name));
        if (getter != null) {
            return invoke(getter, instance);
        }
        Method directMethod = findNoArgMethod(instance.getClass(), name);
        if (directMethod != null) {
            return invoke(directMethod, instance);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull Class<T> resolveEntityType(@NotNull T entity) {
        return (Class<T>) entity.getClass().asSubclass(Entity.class);
    }

    private static @Nullable Method findNoArgMethod(@NotNull Class<?> type, @NotNull String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (name.equals(method.getName()) && method.getParameterTypes().length == 0) {
                    trySetAccessible(method);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static @Nullable Field findField(@NotNull Class<?> type, @NotNull String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                trySetAccessible(field);
                return field;
            } catch (ReflectiveOperationException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static @Nullable Field findCollectionField(@NotNull Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Collection.class.isAssignableFrom(field.getType())) {
                    trySetAccessible(field);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static void trySetAccessible(@NotNull Method method) {
        try {
            method.setAccessible(true);
        } catch (RuntimeException ignored) {
        }
    }

    private static void trySetAccessible(@NotNull Field field) {
        try {
            field.setAccessible(true);
        } catch (RuntimeException ignored) {
        }
    }

    private static @Nullable Object invoke(@NotNull Method method, @Nullable Object instance, @Nullable Object... arguments) {
        try {
            return method.invoke(instance, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke goal support bridge method '" + method.getName() + "'.", exception);
        }
    }

    private static @Nullable Object read(@NotNull Field field, @NotNull Object instance) {
        try {
            return field.get(instance);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read goal support bridge field '" + field.getName() + "'.", exception);
        }
    }

    private static @NotNull String normalize(@NotNull String value) {
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static @NotNull String capitalize(@NotNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class GoalMutationExecutorV1_21_11<T extends Entity> implements RuntimeGoalMutationExecutor<T> {
        private final GoalProfile<T> initialManagedGoals;
        private final boolean applyInitialProfileOnBind;

        private volatile Object nativeHandle;
        private volatile boolean initialProfileApplied;

        private GoalMutationExecutorV1_21_11(
                @NotNull GoalProfile<T> initialManagedGoals,
                boolean applyInitialProfileOnBind
        ) {
            this.initialManagedGoals = Objects.requireNonNull(initialManagedGoals, "initialManagedGoals cannot be null");
            this.applyInitialProfileOnBind = applyInitialProfileOnBind;
        }

        @Override
        public @NotNull GoalProfile<T> initialManagedGoals() {
            return initialManagedGoals;
        }

        @Override
        public void execute(@NotNull RuntimeGoalMutationBatch<T> batch) {
            Objects.requireNonNull(batch, "batch cannot be null");
            Object targetHandle = nativeHandle;
            if (targetHandle == null) {
                targetHandle = resolveNativeHandle(batch.controlledEntity().bukkitEntity());
            }
            if (targetHandle == null) {
                return;
            }
            applyManagedGoalSnapshot(targetHandle, batch.managedGoals());
        }

        private void bindNativeHandle(@NotNull Object nativeHandle) {
            this.nativeHandle = Objects.requireNonNull(nativeHandle, "nativeHandle cannot be null");
            if (applyInitialProfileOnBind && !initialProfileApplied) {
                applyManagedGoalSnapshot(nativeHandle, initialManagedGoals);
                initialProfileApplied = true;
            }
        }
    }

    private static final class SelectorAccessor {
        private final Object owner;
        private final GoalSelectorType selectorType;
        private final Object selector;
        private final Collection<Object> entries;
        private final Method addGoalMethod;

        private SelectorAccessor(
                @NotNull Object owner,
                @NotNull GoalSelectorType selectorType,
                @NotNull Object selector,
                @NotNull Collection<Object> entries,
                @Nullable Method addGoalMethod
        ) {
            this.owner = owner;
            this.selectorType = selectorType;
            this.selector = selector;
            this.entries = entries;
            this.addGoalMethod = addGoalMethod;
        }

        private static @NotNull SelectorAccessor resolve(@NotNull Object owner, @NotNull GoalSelectorType selectorType) {
            Object selector = resolveSelector(owner, selectorType);
            if (selector == null) {
                throw new IllegalStateException("Could not resolve selector '" + selectorType + "' for latest-family goal support.");
            }

            Object entries = resolveCollectionValue(selector, "getAvailableGoals", "getGoals", "availableGoals", "goals", "entries");
            if (!(entries instanceof Collection)) {
                throw new IllegalStateException("Could not resolve latest-family goal-entry collection for selector '" + selectorType + "'.");
            }
            return new SelectorAccessor(
                    owner,
                    selectorType,
                    selector,
                    (Collection<Object>) entries,
                    findAddGoalMethod(selector.getClass())
            );
        }

        private void removeManagedEntries() {
            for (Iterator<Object> iterator = entries.iterator(); iterator.hasNext(); ) {
                if (ManagedGoalEntry.resolve(iterator.next()) != null) {
                    iterator.remove();
                }
            }
        }

        private void add(int priority, @NotNull Object goal) {
            if (addGoalMethod != null) {
                Object resolvedGoal = goal;
                Class<?> goalParameterType = addGoalMethod.getParameterTypes()[1];
                if (!goalParameterType.isInstance(resolvedGoal)) {
                    resolvedGoal = createNativeGoal(goalParameterType, goal);
                }
                if (goalParameterType.isInstance(resolvedGoal)) {
                    invoke(addGoalMethod, selector, Integer.valueOf(priority), resolvedGoal);
                    return;
                }
            }
            entries.add(new SimpleWrappedGoal(priority, goal));
        }

        private @NotNull Object createVanillaGoal(@NotNull VanillaGoalSpec goalSpec) {
            return new ManagedVanillaGoalBridge(goalSpec.key());
        }

        private @NotNull Object createNativeGoal(@NotNull Class<?> goalParameterType, @NotNull Object goal) {
            if (goal instanceof ManagedVanillaGoalBridge) {
                Object nativeGoal = tryCreateNativeVanillaGoal(((ManagedVanillaGoalBridge) goal).key());
                if (goalParameterType.isInstance(nativeGoal)) {
                    return nativeGoal;
                }
            }
            return goal;
        }

        private @NotNull Object tryCreateNativeVanillaGoal(@NotNull VanillaGoalKey key) {
            try {
                switch (key) {
                    case FLOAT:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.FloatGoal", owner);
                    case MELEE_ATTACK:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.MeleeAttackGoal", owner, Double.valueOf(1.0D), Boolean.TRUE);
                    case RANDOM_STROLL_LAND:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.RandomStrollGoal", owner, Double.valueOf(1.0D));
                    case LOOK_AT_PLAYER:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.LookAtPlayerGoal", owner, playerGoalTargetType(), Float.valueOf(8.0F));
                    case RANDOM_LOOK_AROUND:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.RandomLookAroundGoal", owner);
                    case HURT_BY_TARGET:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal", owner, new Class[0]);
                    case NEAREST_ATTACKABLE_TARGET:
                        return instantiateGoal(
                                "net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal",
                                owner,
                                playerGoalTargetType(),
                                Boolean.TRUE
                        );
                    default:
                        return new ManagedVanillaGoalBridge(key);
                }
            } catch (RuntimeException ignored) {
                return new ManagedVanillaGoalBridge(key);
            }
        }

        private @NotNull Class<?> playerGoalTargetType() {
            try {
                return Class.forName("net.minecraft.world.entity.player.Player");
            } catch (ClassNotFoundException exception) {
                return Entity.class;
            }
        }

        private static @NotNull Object instantiateGoal(@NotNull String goalClassName, @NotNull Object... arguments) {
            try {
                Class<?> goalType = Class.forName(goalClassName);
                Constructor<?> constructor = requireCompatibleConstructor(goalType, arguments);
                trySetAccessible(constructor);
                return constructor.newInstance(arguments);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not instantiate latest-family goal type '" + goalClassName + "'.", exception);
            }
        }

        private static void trySetAccessible(@NotNull Constructor<?> constructor) {
            try {
                constructor.setAccessible(true);
            } catch (RuntimeException ignored) {
            }
        }

        private static void trySetAccessible(@NotNull Method method) {
            try {
                method.setAccessible(true);
            } catch (RuntimeException ignored) {
            }
        }

        private static @NotNull Constructor<?> requireCompatibleConstructor(
                @NotNull Class<?> goalType,
                @NotNull Object[] arguments
        ) {
            for (Constructor<?> constructor : goalType.getDeclaredConstructors()) {
                if (isCompatible(constructor.getParameterTypes(), arguments)) {
                    return constructor;
                }
            }
            throw new IllegalStateException("Could not find a compatible constructor for latest-family goal type '" + goalType.getName() + "'.");
        }

        private static boolean isCompatible(@NotNull Class<?>[] parameterTypes, @NotNull Object[] arguments) {
            if (parameterTypes.length != arguments.length) {
                return false;
            }
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!isCompatible(parameterTypes[index], arguments[index])) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isCompatible(@NotNull Class<?> parameterType, @Nullable Object argument) {
            if (argument == null) {
                return !parameterType.isPrimitive();
            }
            if (parameterType.isPrimitive()) {
                return primitiveWrapper(parameterType).isInstance(argument);
            }
            if (parameterType.isArray()) {
                return argument.getClass().isArray() && parameterType.getComponentType().isAssignableFrom(argument.getClass().getComponentType());
            }
            return parameterType.isAssignableFrom(argument.getClass());
        }

        private static @NotNull Class<?> primitiveWrapper(@NotNull Class<?> primitiveType) {
            if (primitiveType == Integer.TYPE) {
                return Integer.class;
            }
            if (primitiveType == Boolean.TYPE) {
                return Boolean.class;
            }
            if (primitiveType == Double.TYPE) {
                return Double.class;
            }
            if (primitiveType == Float.TYPE) {
                return Float.class;
            }
            if (primitiveType == Long.TYPE) {
                return Long.class;
            }
            if (primitiveType == Short.TYPE) {
                return Short.class;
            }
            if (primitiveType == Byte.TYPE) {
                return Byte.class;
            }
            if (primitiveType == Character.TYPE) {
                return Character.class;
            }
            return primitiveType;
        }

        private static @Nullable Method findAddGoalMethod(@NotNull Class<?> selectorType) {
            Class<?> current = selectorType;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == 2
                            && (Integer.TYPE.equals(parameterTypes[0]) || Integer.class.equals(parameterTypes[0]))
                            && ("addGoal".equals(method.getName()) || "a".equals(method.getName()))) {
                        trySetAccessible(method);
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }
    }

    private static final class ManagedGoalEntry {
        private final VanillaGoalKey vanillaKey;
        private final CustomGoalKey customKey;
        private final int priority;

        private ManagedGoalEntry(@Nullable VanillaGoalKey vanillaKey, @Nullable CustomGoalKey customKey, int priority) {
            this.vanillaKey = vanillaKey;
            this.customKey = customKey;
            this.priority = priority;
        }

        private static @Nullable ManagedGoalEntry resolve(@Nullable Object selectorEntry) {
            if (selectorEntry == null) {
                return null;
            }
            Object goal = resolveGoal(selectorEntry);
            if (goal instanceof ManagedVanillaGoalBridge) {
                return new ManagedGoalEntry(((ManagedVanillaGoalBridge) goal).key(), null, resolvePriority(selectorEntry));
            }
            if (goal instanceof ManagedCustomGoalBridge) {
                return new ManagedGoalEntry(null, ((ManagedCustomGoalBridge) goal).key(), resolvePriority(selectorEntry));
            }
            VanillaGoalKey vanillaKey = resolveVanillaGoalKey(goal.getClass().getSimpleName());
            if (vanillaKey == null) {
                return null;
            }
            return new ManagedGoalEntry(vanillaKey, null, resolvePriority(selectorEntry));
        }

        private static @NotNull Object resolveGoal(@NotNull Object selectorEntry) {
            Object directGoal = resolveNamedMember(selectorEntry, "goal");
            if (directGoal != null) {
                return directGoal;
            }
            Method goalMethod = findNoArgMethod(selectorEntry.getClass(), "getGoal");
            if (goalMethod != null) {
                Object goal = invoke(goalMethod, selectorEntry);
                if (goal != null) {
                    return goal;
                }
            }
            return selectorEntry;
        }

        private static int resolvePriority(@NotNull Object selectorEntry) {
            Object priority = resolveNamedMember(selectorEntry, "priority");
            if (priority instanceof Number) {
                return ((Number) priority).intValue();
            }
            Method priorityMethod = findNoArgMethod(selectorEntry.getClass(), "getPriority");
            if (priorityMethod != null) {
                Object value = invoke(priorityMethod, selectorEntry);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }
            return 0;
        }

        private @Nullable VanillaGoalKey vanillaKeyOrNull() {
            return vanillaKey;
        }

        private @Nullable CustomGoalKey customKeyOrNull() {
            return customKey;
        }

        private int priority() {
            return priority;
        }
    }

    private static @Nullable VanillaGoalKey resolveVanillaGoalKey(@NotNull String simpleName) {
        String normalizedName = normalize(simpleName);
        if (matches(normalizedName, "PATHFINDERGOALFLOAT", "FLOATGOAL")) {
            return VanillaGoalKey.FLOAT;
        }
        if (matches(normalizedName, "PATHFINDERGOALMELEEATTACK", "MELEEATTACKGOAL")) {
            return VanillaGoalKey.MELEE_ATTACK;
        }
        if (matches(
                normalizedName,
                "PATHFINDERGOALRANDOMSTROLLLAND",
                "PATHFINDERGOALRANDOMSTROLL",
                "RANDOMSTROLLLANDGOAL",
                "RANDOMSTROLLGOAL",
                "WATERAVOIDINGRANDOMSTROLLGOAL"
        )) {
            return VanillaGoalKey.RANDOM_STROLL_LAND;
        }
        if (matches(normalizedName, "PATHFINDERGOALLOOKATPLAYER", "LOOKATPLAYERGOAL")) {
            return VanillaGoalKey.LOOK_AT_PLAYER;
        }
        if (matches(normalizedName, "PATHFINDERGOALRANDOMLOOKAROUND", "RANDOMLOOKAROUNDGOAL")) {
            return VanillaGoalKey.RANDOM_LOOK_AROUND;
        }
        if (matches(normalizedName, "PATHFINDERGOALHURTBYTARGET", "HURTBYTARGETGOAL")) {
            return VanillaGoalKey.HURT_BY_TARGET;
        }
        if (matches(normalizedName, "PATHFINDERGOALNEARESTATTACKABLETARGET", "NEARESTATTACKABLETARGETGOAL")) {
            return VanillaGoalKey.NEAREST_ATTACKABLE_TARGET;
        }
        return null;
    }

    private static boolean matches(@NotNull String actual, @NotNull String... expectedValues) {
        for (String expected : expectedValues) {
            if (expected.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    private static final class ManagedVanillaGoalBridge {
        private final VanillaGoalKey key;

        private ManagedVanillaGoalBridge(@NotNull VanillaGoalKey key) {
            this.key = Objects.requireNonNull(key, "key cannot be null");
        }

        private @NotNull VanillaGoalKey key() {
            return key;
        }
    }

    private static final class ManagedCustomGoalBridge {
        private final CustomGoalKey key;

        private ManagedCustomGoalBridge(@NotNull CustomGoalKey key) {
            this.key = Objects.requireNonNull(key, "key cannot be null");
        }

        private @NotNull CustomGoalKey key() {
            return key;
        }
    }

    private static final class SimpleWrappedGoal {
        private final int priority;
        private final Object goal;

        private SimpleWrappedGoal(int priority, @NotNull Object goal) {
            this.priority = priority;
            this.goal = Objects.requireNonNull(goal, "goal cannot be null");
        }

        public int getPriority() {
            return priority;
        }

        public @NotNull Object getGoal() {
            return goal;
        }
    }
}
