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
package tech.guilhermekaua.spigotboot.v1_16_5.entity;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalManager;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.UnsupportedGoalOperationException;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutation;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationBatch;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;

final class EntityGoalSupportV1_16_5 {
    private static final VersionGoalSupportMetadata METADATA = new VersionGoalSupportMetadata(
            EnumSet.allOf(VanillaGoalKey.class),
            true,
            true,
            true
    );

    private EntityGoalSupportV1_16_5() {
    }

    static @NotNull VersionGoalSupportMetadata metadata() {
        return METADATA;
    }

    static <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createSpawnExecutor(
            @NotNull EntityTemplate<T> template,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        requireZombieBaseType(template.baseType(), template.bukkitType(), minecraftVersion);
        return new GoalMutationExecutorV1_16_5<T>(template.goalProfile(), true, minecraftVersion);
    }

    static <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createAttachedExecutor(
            @NotNull CustomEntityBaseType baseType,
            @NotNull T entity,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        T resolvedEntity = Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        Class<T> entityType = entityType(resolvedEntity);
        requireZombieBaseType(baseType, entityType, minecraftVersion);
        return new GoalMutationExecutorV1_16_5<T>(snapshotManagedGoals(baseType, resolvedEntity, minecraftVersion), false, minecraftVersion);
    }

    static void bindNativeHandle(@NotNull NativeEntityLifecycle<?> lifecycle, @NotNull Object nativeHandle) {
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        Objects.requireNonNull(nativeHandle, "nativeHandle cannot be null");

        ControlledEntity<?> controlledEntity = lifecycle.handle();
        if (controlledEntity == null) {
            return;
        }
        GoalManager<?> goalManager = controlledEntity.goalManager();
        Object executor = resolveFieldValue(goalManager, "executor");
        if (executor instanceof GoalMutationExecutorV1_16_5) {
            ((GoalMutationExecutorV1_16_5<?>) executor).bindNativeHandle(nativeHandle);
        }
    }

    private static <T extends Entity> @NotNull GoalProfile<T> snapshotManagedGoals(
            @NotNull CustomEntityBaseType baseType,
            @NotNull T entity,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        Class<T> entityType = entityType(entity);
        requireZombieBaseType(baseType, entityType, minecraftVersion);

        GoalProfile.Builder<T> builder = GoalProfile.builder(entityType);
        Object nativeHandle = resolveNativeHandle(entity);
        for (GoalSelectorType selectorType : GoalSelectorType.values()) {
            for (Object entry : resolveSelectorEntries(nativeHandle, selectorType)) {
                ManagedGoalDescriptor descriptor = describeManagedGoal(entry);
                if (descriptor == null) {
                    continue;
                }
                if (descriptor.vanillaKeyOrNull() != null) {
                    builder.add(VanillaGoalSpec.of(selectorType, descriptor.vanillaKeyOrNull(), descriptor.priority()));
                    continue;
                }
                builder.add(CustomGoalSpec.of(selectorType, descriptor.customKeyOrNull(), descriptor.priority()));
            }
        }
        return builder.build();
    }

    private static @NotNull Object resolveNativeHandle(@NotNull Entity entity) {
        Method method = requireZeroArgMethod(entity.getClass(), "getHandle");
        return invoke(method, entity);
    }

    private static void requireZombieBaseType(
            @NotNull CustomEntityBaseType baseType,
            @NotNull Class<? extends Entity> entityType,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        if (baseType == CustomEntityBaseType.ZOMBIE && Zombie.class.isAssignableFrom(entityType)) {
            return;
        }
        throw new UnsupportedGoalOperationException(
                "Minecraft 1.16.5 managed-goal support is only available for zombie entities in this exact-version module.",
                GoalSelectorType.NORMAL,
                entityType,
                minecraftVersion
        );
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull Class<T> entityType(@NotNull T entity) {
        return (Class<T>) entity.getClass().asSubclass(Entity.class);
    }

    private static @NotNull Collection<Object> resolveSelectorEntries(
            @NotNull Object nativeHandle,
            @NotNull GoalSelectorType selectorType
    ) {
        Object selector = resolveFieldValue(nativeHandle, selectorType == GoalSelectorType.NORMAL ? "goalSelector" : "targetSelector");
        if (selector == null) {
            return Collections.emptyList();
        }
        Object entries = resolveNamedFieldValue(selector, new String[]{"d", "c", "goals", "availableGoals"});
        if (entries instanceof Collection) {
            return (Collection<Object>) entries;
        }
        Field collectionField = findCollectionField(selector.getClass());
        if (collectionField == null) {
            return Collections.emptyList();
        }
        return (Collection<Object>) read(collectionField, selector);
    }

    private static @Nullable ManagedGoalDescriptor describeManagedGoal(@NotNull Object entry) {
        Object goal = unwrapGoal(entry);
        if (goal == null) {
            return null;
        }
        int priority = unwrapPriority(entry);
        VanillaGoalKey vanillaKey = vanillaKey(goal);
        if (vanillaKey != null) {
            return ManagedGoalDescriptor.vanilla(vanillaKey, priority);
        }
        CustomGoalKey customKey = customGoalKey(goal);
        if (customKey != null) {
            return ManagedGoalDescriptor.custom(customKey, priority);
        }
        return null;
    }

    private static @Nullable Object unwrapGoal(@NotNull Object entry) {
        Object directGoal = resolveNamedFieldValue(entry, new String[]{"goal", "b", "wrappedGoal"});
        if (directGoal != null) {
            return directGoal;
        }
        Method getter = findZeroArgMethod(entry.getClass(), "getGoal");
        if (getter != null) {
            return invoke(getter, entry);
        }
        return entry;
    }

    private static int unwrapPriority(@NotNull Object entry) {
        Object priority = resolveNamedFieldValue(entry, new String[]{"priority", "a"});
        if (priority instanceof Number) {
            return ((Number) priority).intValue();
        }
        Method getter = findZeroArgMethod(entry.getClass(), "getPriority");
        if (getter != null) {
            Object value = invoke(getter, entry);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return 0;
    }

    private static @Nullable VanillaGoalKey vanillaKey(@NotNull Object goal) {
        if (goal instanceof ManagedVanillaGoalV1_16_5) {
            return ((ManagedVanillaGoalV1_16_5) goal).key();
        }

        String simpleName = goal.getClass().getSimpleName();
        if ("PathfinderGoalFloat".equals(simpleName) || "FloatGoal".equals(simpleName)) {
            return VanillaGoalKey.FLOAT;
        }
        if ("PathfinderGoalMeleeAttack".equals(simpleName) || "MeleeAttackGoal".equals(simpleName)) {
            return VanillaGoalKey.MELEE_ATTACK;
        }
        if ("PathfinderGoalRandomStrollLand".equals(simpleName)
                || "PathfinderGoalRandomStroll".equals(simpleName)
                || "RandomStrollGoal".equals(simpleName)
                || "WaterAvoidingRandomStrollGoal".equals(simpleName)) {
            return VanillaGoalKey.RANDOM_STROLL_LAND;
        }
        if ("PathfinderGoalLookAtPlayer".equals(simpleName) || "LookAtPlayerGoal".equals(simpleName)) {
            return VanillaGoalKey.LOOK_AT_PLAYER;
        }
        if ("PathfinderGoalRandomLookaround".equals(simpleName) || "RandomLookAroundGoal".equals(simpleName)) {
            return VanillaGoalKey.RANDOM_LOOK_AROUND;
        }
        if ("PathfinderGoalHurtByTarget".equals(simpleName) || "HurtByTargetGoal".equals(simpleName)) {
            return VanillaGoalKey.HURT_BY_TARGET;
        }
        if ("PathfinderGoalNearestAttackableTarget".equals(simpleName)
                || "NearestAttackableTargetGoal".equals(simpleName)) {
            return VanillaGoalKey.NEAREST_ATTACKABLE_TARGET;
        }
        return null;
    }

    private static @Nullable CustomGoalKey customGoalKey(@NotNull Object goal) {
        if (goal instanceof ManagedCustomGoalV1_16_5) {
            return ((ManagedCustomGoalV1_16_5) goal).key();
        }
        return null;
    }

    private static final class GoalMutationExecutorV1_16_5<T extends Entity> implements RuntimeGoalMutationExecutor<T> {
        private final GoalProfile<T> initialManagedGoals;
        private final boolean applyInitialProfileOnBind;
        private final MinecraftVersion minecraftVersion;

        private volatile Object nativeHandle;
        private volatile boolean initialProfileApplied;

        private GoalMutationExecutorV1_16_5(
                @NotNull GoalProfile<T> initialManagedGoals,
                boolean applyInitialProfileOnBind,
                @NotNull MinecraftVersion minecraftVersion
        ) {
            this.initialManagedGoals = Objects.requireNonNull(initialManagedGoals, "initialManagedGoals cannot be null");
            this.applyInitialProfileOnBind = applyInitialProfileOnBind;
            this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        }

        @Override
        public @NotNull GoalProfile<T> initialManagedGoals() {
            return initialManagedGoals;
        }

        @Override
        public void execute(@NotNull RuntimeGoalMutationBatch<T> batch) {
            Objects.requireNonNull(batch, "batch cannot be null");
            Object boundNativeHandle = nativeHandle;
            if (boundNativeHandle == null) {
                return;
            }
            for (RuntimeGoalMutation mutation : batch.mutations()) {
                applyMutation(boundNativeHandle, mutation, batch.controlledEntity().baseType(), batch.controlledEntity().bukkitEntity().getClass());
            }
        }

        private void bindNativeHandle(@NotNull Object nativeHandle) {
            this.nativeHandle = Objects.requireNonNull(nativeHandle, "nativeHandle cannot be null");
            if (applyInitialProfileOnBind && !initialProfileApplied) {
                applyInitialProfile(nativeHandle);
                initialProfileApplied = true;
            }
        }

        private void applyInitialProfile(@NotNull Object nativeHandle) {
            for (GoalSelectorType selectorType : GoalSelectorType.values()) {
                for (VanillaGoalSpec goalSpec : initialManagedGoals.vanillaGoals(selectorType)) {
                    addVanillaGoal(nativeHandle, selectorType, goalSpec.key(), goalSpec.priority(), CustomEntityBaseType.ZOMBIE, initialManagedGoals.entityType());
                }
                for (CustomGoalSpec goalSpec : initialManagedGoals.customGoals(selectorType)) {
                    addCustomGoal(nativeHandle, selectorType, goalSpec.key(), goalSpec.priority());
                }
            }
        }

        private void applyMutation(
                @NotNull Object nativeHandle,
                @NotNull RuntimeGoalMutation mutation,
                @NotNull CustomEntityBaseType baseType,
                @NotNull Class<?> entityType
        ) {
            switch (mutation.operation()) {
                case ADD_VANILLA:
                    VanillaGoalSpec vanillaGoalSpec = Objects.requireNonNull(
                            mutation.vanillaGoalSpecOrNull(),
                            "vanillaGoalSpec cannot be null"
                    );
                    removeRecognizedVanilla(nativeHandle, vanillaGoalSpec.selectorType(), vanillaGoalSpec.key());
                    addVanillaGoal(nativeHandle, vanillaGoalSpec.selectorType(), vanillaGoalSpec.key(), vanillaGoalSpec.priority(), baseType, entityType);
                    return;
                case REMOVE_VANILLA:
                    removeRecognizedVanilla(
                            nativeHandle,
                            mutation.selectorType(),
                            Objects.requireNonNull(mutation.vanillaGoalKeyOrNull(), "vanillaGoalKey cannot be null")
                    );
                    return;
                case ADD_CUSTOM:
                    CustomGoalSpec customGoalSpec = Objects.requireNonNull(
                            mutation.customGoalSpecOrNull(),
                            "customGoalSpec cannot be null"
                    );
                    removeManagedCustom(nativeHandle, customGoalSpec.selectorType(), customGoalSpec.key());
                    addCustomGoal(nativeHandle, customGoalSpec.selectorType(), customGoalSpec.key(), customGoalSpec.priority());
                    return;
                case REMOVE_CUSTOM:
                    removeManagedCustom(
                            nativeHandle,
                            mutation.selectorType(),
                            Objects.requireNonNull(mutation.customGoalKeyOrNull(), "customGoalKey cannot be null")
                    );
                    return;
                case CLEAR_SELECTOR:
                    clearManaged(nativeHandle, mutation.selectorType());
                    return;
                default:
                    throw new IllegalStateException("Unsupported goal mutation operation: " + mutation.operation());
            }
        }

        private void addVanillaGoal(
                @NotNull Object nativeHandle,
                @NotNull GoalSelectorType selectorType,
                @NotNull VanillaGoalKey key,
                int priority,
                @NotNull CustomEntityBaseType baseType,
                @NotNull Class<?> entityType
        ) {
            requireZombieBaseType(baseType, entityType.asSubclass(Entity.class), minecraftVersion);
            Object selector = resolveSelector(nativeHandle, selectorType);
            if (selector == null) {
                return;
            }
            invokeSelectorAdd(selector, priority, instantiateVanillaGoal(nativeHandle, key));
        }

        private void addCustomGoal(
                @NotNull Object nativeHandle,
                @NotNull GoalSelectorType selectorType,
                @NotNull CustomGoalKey key,
                int priority
        ) {
            Object selector = resolveSelector(nativeHandle, selectorType);
            if (selector == null) {
                return;
            }
            invokeSelectorAdd(selector, priority, new ManagedCustomGoalV1_16_5(key));
        }

        private void clearManaged(@NotNull Object nativeHandle, @NotNull GoalSelectorType selectorType) {
            Collection<Object> entries = resolveSelectorEntries(nativeHandle, selectorType);
            for (Iterator<Object> iterator = entries.iterator(); iterator.hasNext(); ) {
                if (describeManagedGoal(iterator.next()) != null) {
                    iterator.remove();
                }
            }
        }

        private void removeRecognizedVanilla(
                @NotNull Object nativeHandle,
                @NotNull GoalSelectorType selectorType,
                @NotNull VanillaGoalKey key
        ) {
            Collection<Object> entries = resolveSelectorEntries(nativeHandle, selectorType);
            for (Iterator<Object> iterator = entries.iterator(); iterator.hasNext(); ) {
                ManagedGoalDescriptor descriptor = describeManagedGoal(iterator.next());
                if (descriptor != null && key == descriptor.vanillaKeyOrNull()) {
                    iterator.remove();
                }
            }
        }

        private void removeManagedCustom(
                @NotNull Object nativeHandle,
                @NotNull GoalSelectorType selectorType,
                @NotNull CustomGoalKey key
        ) {
            Collection<Object> entries = resolveSelectorEntries(nativeHandle, selectorType);
            for (Iterator<Object> iterator = entries.iterator(); iterator.hasNext(); ) {
                ManagedGoalDescriptor descriptor = describeManagedGoal(iterator.next());
                if (descriptor != null && key.equals(descriptor.customKeyOrNull())) {
                    iterator.remove();
                }
            }
        }

        private @NotNull Object instantiateVanillaGoal(@NotNull Object nativeHandle, @NotNull VanillaGoalKey key) {
            Object instantiated = instantiateNamedVanillaGoal(nativeHandle, key);
            if (instantiated != null) {
                return instantiated;
            }
            return new ManagedVanillaGoalV1_16_5(key);
        }

        private @Nullable Object instantiateNamedVanillaGoal(@NotNull Object nativeHandle, @NotNull VanillaGoalKey key) {
            Class<?> goalType = findRelativeClass(nativeHandle.getClass(), candidateGoalTypeNames(key));
            if (goalType == null) {
                return null;
            }

            for (Constructor<?> constructor : goalType.getDeclaredConstructors()) {
                Object[] arguments = buildConstructorArguments(constructor.getParameterTypes(), nativeHandle);
                if (arguments == null) {
                    continue;
                }
                try {
                    constructor.setAccessible(true);
                    return constructor.newInstance(arguments);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return null;
        }

        private @Nullable Object[] buildConstructorArguments(@NotNull Class<?>[] parameterTypes, @NotNull Object nativeHandle) {
            Object[] arguments = new Object[parameterTypes.length];
            for (int index = 0; index < parameterTypes.length; index++) {
                Class<?> parameterType = parameterTypes[index];
                if (parameterType.isAssignableFrom(nativeHandle.getClass())) {
                    arguments[index] = nativeHandle;
                    continue;
                }
                if (parameterType == double.class || parameterType == Double.class) {
                    arguments[index] = Double.valueOf(1.0D);
                    continue;
                }
                if (parameterType == float.class || parameterType == Float.class) {
                    arguments[index] = Float.valueOf(8.0F);
                    continue;
                }
                if (parameterType == boolean.class || parameterType == Boolean.class) {
                    arguments[index] = Boolean.TRUE;
                    continue;
                }
                if (parameterType == int.class || parameterType == Integer.class) {
                    arguments[index] = Integer.valueOf(0);
                    continue;
                }
                if (parameterType == Class.class) {
                    arguments[index] = Entity.class;
                    continue;
                }
                if (parameterType.isArray() && parameterType.getComponentType() == Class.class) {
                    arguments[index] = Array.newInstance(Class.class, 0);
                    continue;
                }
                return null;
            }
            return arguments;
        }
    }

    private static void invokeSelectorAdd(@NotNull Object selector, int priority, @NotNull Object goal) {
        Method addMethod = findSelectorAddMethod(selector.getClass(), goal.getClass());
        if (addMethod != null) {
            invoke(addMethod, selector, Integer.valueOf(priority), goal);
            return;
        }

        Field collectionField = findCollectionField(selector.getClass());
        if (collectionField == null) {
            throw new IllegalStateException("Could not resolve goal-entry collection for selector '" + selector.getClass().getName() + "'.");
        }
        @SuppressWarnings("unchecked")
        Collection<Object> entries = (Collection<Object>) read(collectionField, selector);
        entries.add(new SimpleWrappedGoal(priority, goal));
    }

    private static @Nullable Method findSelectorAddMethod(@NotNull Class<?> selectorType, @NotNull Class<?> goalType) {
        Method[] methods = selectorType.getDeclaredMethods();
        for (Method method : methods) {
            if (!("a".equals(method.getName()) || "addGoal".equals(method.getName()))) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 2) {
                continue;
            }
            if (!(parameterTypes[0] == int.class || parameterTypes[0] == Integer.TYPE)) {
                continue;
            }
            if (!parameterTypes[1].isAssignableFrom(goalType) && !parameterTypes[1].isAssignableFrom(Object.class)) {
                continue;
            }
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static @Nullable Object resolveSelector(@NotNull Object nativeHandle, @NotNull GoalSelectorType selectorType) {
        return resolveFieldValue(nativeHandle, selectorType == GoalSelectorType.NORMAL ? "goalSelector" : "targetSelector");
    }

    private static @Nullable Class<?> findRelativeClass(@NotNull Class<?> anchorType, @NotNull String[] simpleNames) {
        String packageName = anchorType.getPackage().getName();
        for (String simpleName : simpleNames) {
            try {
                return Class.forName(packageName + "." + simpleName);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static @NotNull String[] candidateGoalTypeNames(@NotNull VanillaGoalKey key) {
        switch (key) {
            case FLOAT:
                return new String[]{"PathfinderGoalFloat", "FloatGoal"};
            case MELEE_ATTACK:
                return new String[]{"PathfinderGoalMeleeAttack", "MeleeAttackGoal"};
            case RANDOM_STROLL_LAND:
                return new String[]{"PathfinderGoalRandomStrollLand", "PathfinderGoalRandomStroll", "RandomStrollGoal"};
            case LOOK_AT_PLAYER:
                return new String[]{"PathfinderGoalLookAtPlayer", "LookAtPlayerGoal"};
            case RANDOM_LOOK_AROUND:
                return new String[]{"PathfinderGoalRandomLookaround", "RandomLookAroundGoal"};
            case HURT_BY_TARGET:
                return new String[]{"PathfinderGoalHurtByTarget", "HurtByTargetGoal"};
            case NEAREST_ATTACKABLE_TARGET:
                return new String[]{"PathfinderGoalNearestAttackableTarget", "NearestAttackableTargetGoal"};
            default:
                throw new IllegalStateException("Unsupported vanilla goal key: " + key);
        }
    }

    private static @Nullable Field findCollectionField(@NotNull Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Collection.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private static @Nullable Method findZeroArgMethod(@NotNull Class<?> type, @NotNull String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (name.equals(method.getName()) && method.getParameterTypes().length == 0) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static @NotNull Method requireZeroArgMethod(@NotNull Class<?> type, @NotNull String name) {
        Method method = findZeroArgMethod(type, name);
        if (method != null) {
            return method;
        }
        throw new IllegalStateException("Could not resolve zero-argument method '" + name + "' on " + type.getName() + ".");
    }

    private static @Nullable Object resolveNamedFieldValue(@NotNull Object instance, @NotNull String[] candidates) {
        for (String candidate : candidates) {
            Object value = resolveFieldValue(instance, candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static @Nullable Object resolveFieldValue(@NotNull Object instance, @NotNull String fieldName) {
        Field field = findField(instance.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        return read(field, instance);
    }

    private static @Nullable Field findField(@NotNull Class<?> type, @NotNull String fieldName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static @NotNull Object read(@NotNull Field field, @NotNull Object instance) {
        try {
            return field.get(instance);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not read field '" + field.getName() + "'.", exception);
        }
    }

    private static @NotNull Object invoke(@NotNull Method method, @Nullable Object instance, @Nullable Object... arguments) {
        try {
            return method.invoke(instance, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke method '" + method.getName() + "'.", exception);
        }
    }

    private static final class ManagedGoalDescriptor {
        private final VanillaGoalKey vanillaKey;
        private final CustomGoalKey customKey;
        private final int priority;

        private ManagedGoalDescriptor(@Nullable VanillaGoalKey vanillaKey, @Nullable CustomGoalKey customKey, int priority) {
            this.vanillaKey = vanillaKey;
            this.customKey = customKey;
            this.priority = priority;
        }

        private static @NotNull ManagedGoalDescriptor vanilla(@NotNull VanillaGoalKey key, int priority) {
            return new ManagedGoalDescriptor(Objects.requireNonNull(key, "key cannot be null"), null, priority);
        }

        private static @NotNull ManagedGoalDescriptor custom(@NotNull CustomGoalKey key, int priority) {
            return new ManagedGoalDescriptor(null, Objects.requireNonNull(key, "key cannot be null"), priority);
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

    static final class ManagedVanillaGoalV1_16_5 {
        private final VanillaGoalKey key;

        ManagedVanillaGoalV1_16_5(@NotNull VanillaGoalKey key) {
            this.key = Objects.requireNonNull(key, "key cannot be null");
        }

        @NotNull VanillaGoalKey key() {
            return key;
        }
    }

    static final class ManagedCustomGoalV1_16_5 {
        private final CustomGoalKey key;

        ManagedCustomGoalV1_16_5(@NotNull CustomGoalKey key) {
            this.key = Objects.requireNonNull(key, "key cannot be null");
        }

        @NotNull CustomGoalKey key() {
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
    }
}
