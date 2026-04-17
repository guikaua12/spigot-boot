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
package tech.guilhermekaua.spigotboot.v1_17_1.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalOperationException;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.UnsupportedGoalOperationException;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalSpec;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationBatch;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityStrategyBundleSelector;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.EntityStrategyBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.VersionEntrypoint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Version-local spawn and attach scaffold for the Minecraft 1.17-1.18.2 family.
 *
 * @since 2.0.2
 */
public final class EntityFactoryV1_17_1
        implements VersionEntrypoint,
        PaperTrackingBindingStrategy_1_21_plus.Support,
        PaperFreshSpawnStrategy_1_21_plus.Support,
        PaperReplacementStrategy_1_21_plus.Support {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 17, 1);
    private static final String SUPPORTED_FAMILY = "1.17-1.18.2";
    private static final VersionCapabilities ENTITY_CAPABILITIES = new VersionCapabilities(
            EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
            true,
            EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
            EntityWorldRegistrationMode.REFERENCE_REWRITE
    );
    private static final VersionBindings ENTITY_BINDINGS = new VersionBindings(
            new EntityFreshSpawnBinding(
                    Arrays.asList(
                            NativeEntityConstructorShape.LEVEL_AND_POSITION,
                            NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL,
                            NativeEntityConstructorShape.LEVEL_ONLY
                    ),
                    EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                    true,
                    true
            ),
            new EntityReplacementBinding(
                    EntityWorldRegistrationMode.REFERENCE_REWRITE,
                    true,
                    true
            )
    );
    private static final EntityStrategyBundle ENTITY_STRATEGY_BUNDLE = EntityStrategyBundleSelector.select(
            VERSION,
            ENTITY_CAPABILITIES,
            ENTITY_BINDINGS
    );
    private static final PaperFreshSpawnStrategy_1_21_plus FRESH_SPAWN_STRATEGY =
            EntityStrategyBundleSelector.requirePaperFreshSpawnStrategy(ENTITY_STRATEGY_BUNDLE.freshSpawn());
    private static final PaperReplacementStrategy_1_21_plus REPLACEMENT_STRATEGY =
            EntityStrategyBundleSelector.requirePaperReplacementStrategy(ENTITY_STRATEGY_BUNDLE.replacement());
    private static final VersionGoalSupportMetadata GOAL_SUPPORT_METADATA = new VersionGoalSupportMetadata(
            EnumSet.allOf(VanillaGoalKey.class),
            true,
            true,
            true
    );
    private static final GeneratedNativeEntityClassFactory CLASS_FACTORY = new GeneratedNativeEntityClassFactory();
    private static final EntityHookBinderV1_17_1 HOOK_BINDER = new EntityHookBinderV1_17_1();
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUDED_BASE_TYPES = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    private static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUDED_BASE_TYPES = EnumSet.of(CustomEntityBaseType.COW);
    private static final EnumSet<CustomEntityBaseType> ADVERTISED_SUPPORTED_BASE_TYPES = EnumSet.of(
            CustomEntityBaseType.ZOMBIE,
            CustomEntityBaseType.SKELETON
    );
    private static final Map<CustomEntityBaseType, EntityMetadata> METADATA_REGISTRY = createMetadataRegistry();
    private static final Map<Class<?>, ResolvedEntityTypeMetadata> GENERATED_ENTITY_TYPES =
            new LinkedHashMap<Class<?>, ResolvedEntityTypeMetadata>();

    private final Map<UUID, ControlledEntity<?>> attachedEntities = new LinkedHashMap<UUID, ControlledEntity<?>>();
    private final Map<CustomEntityBaseType, EntityMetadata> metadataRegistry = METADATA_REGISTRY;
    private final Map<CustomEntityBaseType, ResolvedSpawnMetadata> spawnMetadataRegistry =
            new LinkedHashMap<CustomEntityBaseType, ResolvedSpawnMetadata>();

    public static @NotNull VersionCapabilities entityCapabilities() {
        return ENTITY_CAPABILITIES;
    }

    public static @NotNull VersionBindings entityBindings() {
        return ENTITY_BINDINGS;
    }

    public @NotNull VersionGoalSupportMetadata entityGoalSupportMetadata() {
        return GOAL_SUPPORT_METADATA;
    }

    public <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createSpawnGoalMutationExecutor(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        requireMetadata(template.baseType());
        return new GoalMutationExecutorBridge<T>(this, template.bukkitType(), template.goalProfile());
    }

    public <T extends Entity> @NotNull RuntimeGoalMutationExecutor<T> createAttachedGoalMutationExecutor(
            @NotNull CustomEntityBaseType baseType,
            @NotNull T entity,
            @NotNull MinecraftVersion minecraftVersion
    ) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        T resolvedEntity = Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion cannot be null");
        requireMetadata(baseType);
        Class<T> entityType = entityType(resolvedEntity);
        return new GoalMutationExecutorBridge<T>(
                this,
                entityType,
                snapshotManagedGoals(entityType, resolveCurrentNativeHandle(resolvedEntity))
        );
    }

    @Override
    public @NotNull VersionCapabilities capabilities() {
        return ENTITY_CAPABILITIES;
    }

    @Override
    public @NotNull VersionBindings bindings() {
        return ENTITY_BINDINGS;
    }

    @Override
    public boolean supports(@NotNull CustomEntityBaseType baseType) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return ADVERTISED_SUPPORTED_BASE_TYPES.contains(baseType);
    }

    @Override
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        return FRESH_SPAWN_STRATEGY.spawn(this, template, spawnOptions, lifecycle);
    }

    @Override
    public <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        requireAdvertisedSupportedBaseType(resolveBaseType(entity), "attach");

        ControlledEntity<?> existing = attachedEntities.get(entity.getUniqueId());
        if (existing != null && !existing.isRemoved() && existing.bukkitEntity().equals(entity)) {
            @SuppressWarnings("unchecked")
            ControlledEntity<T> rebound = (ControlledEntity<T>) existing;
            return rebound;
        }

        final ControlledEntity<T> attached = REPLACEMENT_STRATEGY.attach(this, entity, lifecycle);
        attachedEntities.put(entity.getUniqueId(), attached);
        if (attached instanceof AbstractRuntimeControlledEntity) {
            ((AbstractRuntimeControlledEntity<?>) attached).bindRemovalCallback(new Runnable() {
                @Override
                public void run() {
                    attachedEntities.remove(entity.getUniqueId());
                }
            });
        }
        return attached;
    }

    @Override
    public <T extends Entity> PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn prepareFreshSpawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        EntityMetadata metadata = requireMetadata(template.baseType());
        return new PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn(
                resolveSpawnMetadata(metadata, spawnOptions.location())
        );
    }

    @Override
    public @NotNull Object createNativeEntity(
            @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
            @NotNull Location location
    ) {
        Objects.requireNonNull(preparedSpawn, "preparedSpawn cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        return createFreshNativeEntity(resolvePreparedSpawnMetadata(preparedSpawn), location);
    }

    @Override
    public <T extends Entity> void bindLifecycleToNativeEntity(
            @NotNull Object nativeEntity,
            @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        Objects.requireNonNull(preparedSpawn, "preparedSpawn cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        ResolvedSpawnMetadata spawnMetadata = resolvePreparedSpawnMetadata(preparedSpawn);
        bindRuntimeLifecycle(nativeEntity, spawnMetadata.resolvedMetadata(), lifecycle);
        applyManagedGoalSnapshot(nativeEntity, lifecycle.handle().goalManager().managedGoals());
    }

    @Override
    public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        return resolveBukkitEntity(nativeEntity);
    }

    @Override
    public @NotNull PaperFreshSpawnStrategy_1_21_plus.TrackingHandles resolveTrackingHandles(@NotNull Object nativeEntity) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        TrackedEntityState trackedEntityState = resolveTrackedEntityState(nativeEntity);
        return new PaperFreshSpawnStrategy_1_21_plus.TrackingHandles(
                trackedEntityState.trackedEntity(),
                trackedEntityState.serverEntity()
        );
    }

    @Override
    public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(world.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, world);
    }

    @Override
    public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        return resolveNativeHandle(entity);
    }

    @Override
    public <T extends Entity> PaperReplacementStrategy_1_21_plus.PreparedReplacement prepareReplacement(
            @NotNull T entity,
            @NotNull Object currentNativeHandle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        requireAdvertisedSupportedBaseType(resolveBaseType(entity), "attach");
        return new PaperReplacementStrategy_1_21_plus.PreparedReplacement(
                new ReplacementMetadata(currentNativeHandle.getClass())
        );
    }

    @Override
    public @NotNull Object allocateReplacementHandle(
            @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
    ) {
        Objects.requireNonNull(preparedReplacement, "preparedReplacement cannot be null");
        return ReflectionSupport.allocateInstance(resolvePreparedReplacementMetadata(preparedReplacement).replacementType());
    }

    @Override
    public <T extends Entity> void bindLifecycleToReplacement(
            @NotNull Object replacementHandle,
            @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        Objects.requireNonNull(preparedReplacement, "preparedReplacement cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        if (replacementHandle instanceof LifecycleAwareNativeEntity) {
            ((LifecycleAwareNativeEntity) replacementHandle).spigotBootBindLifecycle(lifecycle);
        }
    }

    @Override
    public @NotNull PaperReplacementStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        TrackedEntityState trackedEntityState = resolveTrackedEntityState(replacementHandle);
        return new PaperReplacementStrategy_1_21_plus.TrackingHandles(
                trackedEntityState.trackedEntity(),
                trackedEntityState.serverEntity()
        );
    }

    @Override
    public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindBukkitZombieInternal(entity, replacementHandle);
    }

    @Override
    public void rebindModernBukkitBridge(
            @NotNull Entity bukkitEntity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(bukkitEntity, "bukkitEntity cannot be null");
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindModernBukkitBridgeInternal(bukkitEntity, oldHandle, replacementHandle);
    }

    @Override
    public void replaceWorldReferences(
            @NotNull EntityPublicationFamily family,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (family != EntityPublicationFamily.SECTION_MANAGER) {
            throw unsupportedPublicationFamily(family, "world reference replacement");
        }
        replaceModernWorldReferences(oldHandle, replacementHandle);
    }

    @Override
    public void replaceModernWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        replaceModernWorldReferencesInternal(oldHandle, replacementHandle);
    }

    @Override
    public void rewireModernVehicleAndPassengerReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rewireModernVehicleAndPassengerReferencesInternal(oldHandle, replacementHandle);
    }

    @Override
    public void refreshModernBukkitWrappers(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        refreshModernBukkitWrappersInternal(entity);
    }

    @Override
    public void markModernEntityRemoved(@NotNull Object oldHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        markModernEntityRemovedInternal(oldHandle);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> void scheduleRepairPass(
            @NotNull NativeEntityLifecycle<T> lifecycle,
            @NotNull T entity,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        schedulePaperReplacementRepairPass(
                (NativeEntityLifecycle<Entity>) lifecycle,
                entity,
                currentNativeHandle,
                replacementHandle
        );
    }

    private static synchronized @NotNull ResolvedEntityTypeMetadata resolveGeneratedTypeMetadata(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        ResolvedEntityTypeMetadata resolvedMetadata = GENERATED_ENTITY_TYPES.get(nativeType);
        if (resolvedMetadata != null) {
            return resolvedMetadata;
        }

        Collection<GeneratedNativeHookSpec> hookSpecs = HOOK_BINDER.hookSpecs(nativeType);
        if (hookSpecs.isEmpty()) {
            throw new UnsupportedOperationException(
                    "Minecraft " + SUPPORTED_FAMILY + " does not expose any supported fresh-spawn hooks for base type '"
                            + metadata.baseType() + "'."
            );
        }

        Class<?> generatedType = CLASS_FACTORY.createSubclass(
                nativeType,
                generatedClassName(metadata, nativeType),
                hookSpecs
        );
        resolvedMetadata = new ResolvedEntityTypeMetadata(metadata, nativeType, generatedType, hookSpecs);
        GENERATED_ENTITY_TYPES.put(nativeType, resolvedMetadata);
        return resolvedMetadata;
    }

    @SuppressWarnings("unchecked")
    private void bindRuntimeLifecycle(
            @NotNull Object nativeEntity,
            @NotNull ResolvedEntityTypeMetadata resolvedMetadata,
            @NotNull NativeEntityLifecycle<?> lifecycle
    ) {
        AbstractRuntimeControlledEntity<?> controlledEntity = requireRuntimeLifecycle(lifecycle);
        controlledEntity.bindHookBinder((NativeHookBinder) HOOK_BINDER);
        CLASS_FACTORY.installInterceptor(nativeEntity, resolvedMetadata.hookSpecs());
        CLASS_FACTORY.bindLifecycle(nativeEntity, lifecycle);
    }

    private static @NotNull String generatedClassName(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        return "v1_17_1_"
                + metadata.baseType().name()
                + "_"
                + nativeType.getName().replace('.', '_').replace('$', '_')
                + "_FreshSpawnBridge";
    }

    private static @NotNull ResolvedSpawnMetadata resolvePreparedSpawnMetadata(
            @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn
    ) {
        Object preparedMetadata = preparedSpawn.preparedMetadata();
        if (!(preparedMetadata instanceof ResolvedSpawnMetadata)) {
            throw new IllegalStateException(
                    "Prepared paper fresh-spawn metadata did not contain a ResolvedSpawnMetadata instance."
            );
        }
        return (ResolvedSpawnMetadata) preparedMetadata;
    }

    private synchronized @NotNull ResolvedSpawnMetadata resolveSpawnMetadata(
            @NotNull EntityMetadata metadata,
            @NotNull Location location
    ) {
        ResolvedSpawnMetadata spawnMetadata = spawnMetadataRegistry.get(metadata.baseType());
        if (spawnMetadata != null) {
            return spawnMetadata;
        }

        Entity probeEntity = spawnVanillaEntity(location, metadata);
        try {
            Object probeHandle = resolveNativeHandle(probeEntity);
            ResolvedEntityTypeMetadata resolvedMetadata = resolveGeneratedTypeMetadata(metadata, probeHandle.getClass());
            Method getTypeMethod = ReflectionSupport.findNamedMethod(probeHandle.getClass(), new String[]{"getType"});
            Object nativeEntityType = getTypeMethod == null ? null : ReflectionSupport.invoke(getTypeMethod, probeHandle);
            spawnMetadata = new ResolvedSpawnMetadata(resolvedMetadata, nativeEntityType);
            spawnMetadataRegistry.put(metadata.baseType(), spawnMetadata);
            return spawnMetadata;
        } finally {
            probeEntity.remove();
        }
    }

    private @NotNull Object createFreshNativeEntity(
            @NotNull ResolvedSpawnMetadata spawnMetadata,
            @NotNull Location location
    ) {
        Object levelHandle = resolveNativeWorldHandle(location);
        Object nativeEntity = instantiateNativeEntity(
                spawnMetadata.resolvedMetadata().generatedType(),
                spawnMetadata.nativeEntityType(),
                levelHandle,
                location
        );
        applySpawnLocation(nativeEntity, location);
        return nativeEntity;
    }

    private static @NotNull Object instantiateNativeEntity(
            @NotNull Class<?> generatedType,
            @Nullable Object nativeEntityType,
            @NotNull Object levelHandle,
            @NotNull Location location
    ) {
        Constructor<?> coordinateConstructor = findConstructor(
                generatedType,
                levelHandle.getClass(),
                double.class,
                double.class,
                double.class
        );
        if (coordinateConstructor != null) {
            return ReflectionSupport.instantiate(
                    coordinateConstructor,
                    levelHandle,
                    Double.valueOf(location.getX()),
                    Double.valueOf(location.getY()),
                    Double.valueOf(location.getZ())
            );
        }

        if (nativeEntityType != null) {
            Constructor<?> typedConstructor = findConstructor(
                    generatedType,
                    nativeEntityType.getClass(),
                    levelHandle.getClass()
            );
            if (typedConstructor != null) {
                return ReflectionSupport.instantiate(typedConstructor, nativeEntityType, levelHandle);
            }
        }

        Constructor<?> levelConstructor = findConstructor(generatedType, levelHandle.getClass());
        if (levelConstructor != null) {
            return ReflectionSupport.instantiate(levelConstructor, levelHandle);
        }

        throw new IllegalStateException(
                "Could not resolve a supported constructor for generated entity type '" + generatedType.getName() + "'."
        );
    }

    private static @Nullable Constructor<?> findConstructor(
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
        return null;
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
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static void applySpawnLocation(@NotNull Object nativeEntity, @NotNull Location location) {
        Method moveToMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"moveTo"},
                double.class,
                double.class,
                double.class,
                float.class,
                float.class
        );
        if (moveToMethod != null) {
            ReflectionSupport.invoke(
                    moveToMethod,
                    nativeEntity,
                    Double.valueOf(location.getX()),
                    Double.valueOf(location.getY()),
                    Double.valueOf(location.getZ()),
                    Float.valueOf(location.getYaw()),
                    Float.valueOf(location.getPitch())
            );
            return;
        }

        Method setPosMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"setPos", "setPosRaw"},
                double.class,
                double.class,
                double.class
        );
        if (setPosMethod != null) {
            ReflectionSupport.invoke(
                    setPosMethod,
                    nativeEntity,
                    Double.valueOf(location.getX()),
                    Double.valueOf(location.getY()),
                    Double.valueOf(location.getZ())
            );
        }

        Method setYRotMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"setYRot"}, float.class);
        if (setYRotMethod != null) {
            ReflectionSupport.invoke(setYRotMethod, nativeEntity, Float.valueOf(location.getYaw()));
        }

        Method setXRotMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"setXRot"}, float.class);
        if (setXRotMethod != null) {
            ReflectionSupport.invoke(setXRotMethod, nativeEntity, Float.valueOf(location.getPitch()));
        }
    }

    private static @NotNull ReplacementMetadata resolvePreparedReplacementMetadata(
            @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
    ) {
        Object preparedMetadata = preparedReplacement.preparedMetadata();
        if (!(preparedMetadata instanceof ReplacementMetadata)) {
            throw new IllegalStateException(
                    "Prepared paper replacement metadata did not contain a ReplacementMetadata instance."
            );
        }
        return (ReplacementMetadata) preparedMetadata;
    }

    private static @NotNull CustomEntityBaseType resolveBaseType(@NotNull Entity entity) {
        CustomEntityBaseType baseType = CustomEntityBaseType.fromEntityType(entity.getType());
        if (baseType == null) {
            throw new UnsupportedOperationException(
                    "Could not resolve a logical base type for Bukkit entity type '" + entity.getType().name() + "'."
            );
        }
        return baseType;
    }

    private static void requireAdvertisedSupportedBaseType(@NotNull CustomEntityBaseType baseType, @NotNull String action) {
        if (!ADVERTISED_SUPPORTED_BASE_TYPES.contains(baseType)) {
            throw new UnsupportedOperationException(
                    "Minecraft " + SUPPORTED_FAMILY + " does not support " + action + " for base type '" + baseType + "'."
            );
        }
    }

    private @NotNull EntityMetadata requireMetadata(@NotNull CustomEntityBaseType baseType) {
        EntityMetadata metadata = metadataRegistry.get(baseType);
        if (metadata == null) {
            throw new UnsupportedOperationException(
                    "Minecraft " + SUPPORTED_FAMILY + " does not support spawn and attach for base type '" + baseType + "'."
            );
        }
        return metadata;
    }

    private static @NotNull Map<CustomEntityBaseType, EntityMetadata> createMetadataRegistry() {
        Map<CustomEntityBaseType, EntityMetadata> metadata = new LinkedHashMap<CustomEntityBaseType, EntityMetadata>();
        for (CustomEntityBaseType baseType : ADVERTISED_SUPPORTED_BASE_TYPES) {
            requireExplicitlyAllowedBaseType(baseType);
            metadata.put(baseType, new EntityMetadata(baseType, requireEntityType(baseType)));
        }
        return metadata;
    }

    private static void requireExplicitlyAllowedBaseType(@NotNull CustomEntityBaseType baseType) {
        if (PERMANENT_EXCLUDED_BASE_TYPES.contains(baseType) || PRESERVED_EXCLUDED_BASE_TYPES.contains(baseType)) {
            throw new IllegalStateException(
                    "Minecraft " + SUPPORTED_FAMILY + " cannot advertise support for excluded base type '" + baseType + "'."
            );
        }
    }

    private static @NotNull EntityType requireEntityType(@NotNull CustomEntityBaseType baseType) {
        EntityType entityType = baseType.entityTypeOrNull();
        if (entityType == null) {
            throw new IllegalStateException(
                    "Minecraft " + SUPPORTED_FAMILY + " cannot advertise support for base type '" + baseType
                            + "' because Bukkit EntityType is unavailable."
            );
        }
        return entityType;
    }

    private static @NotNull Entity spawnVanillaEntity(
            @NotNull Location location,
            @NotNull EntityMetadata metadata
    ) {
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        return world.spawnEntity(location.clone(), metadata.entityType());
    }

    private static @NotNull Object resolveNativeHandle(@NotNull Entity entity) {
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(entity.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, entity);
    }

    private static @NotNull Entity resolveBukkitEntity(@NotNull Object nmsEntity) {
        Method getBukkitEntityMethod = ReflectionSupport.requireNamedMethod(
                nmsEntity.getClass(),
                new String[]{"getBukkitEntity"}
        );
        return (Entity) ReflectionSupport.invoke(getBukkitEntityMethod, nmsEntity);
    }

    private static void rebindBukkitZombieInternal(@NotNull Entity entity, @NotNull Object replacementHandle) {
        Method setHandleMethod = ReflectionSupport.requireCompatibleMethod(
                entity.getClass(),
                new String[]{"setHandle"},
                replacementHandle.getClass()
        );
        ReflectionSupport.invoke(setHandleMethod, entity, replacementHandle);
    }

    private static void rebindModernBukkitBridgeInternal(
            @NotNull Entity bukkitEntity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field bukkitEntityField = findEntityHandleField("bukkitEntity");
        if (bukkitEntityField == null) {
            return;
        }
        ReflectionSupport.writeField(bukkitEntityField, replacementHandle, bukkitEntity);
        ReflectionSupport.writeField(bukkitEntityField, oldHandle, null);
    }

    private static void replaceModernWorldReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Object level = ReflectionSupport.readField(
                requireEntityHandleField("level", "t"),
                oldHandle
        );
        replaceSectionManagerVisibleStorage(level, oldHandle, replacementHandle);
        updateTrackedEntity(oldHandle, replacementHandle);
        rebindSectionManagerLevelCallback(oldHandle, replacementHandle);
        replaceLifecycleCollections(level, oldHandle, replacementHandle);
    }

    private static void replaceSectionManagerVisibleStorage(
            @Nullable Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (level == null) {
            return;
        }

        Field entityManagerField = ReflectionSupport.findField(level.getClass(), "entityManager", "G");
        if (entityManagerField == null) {
            return;
        }

        Object entityManager = ReflectionSupport.readField(entityManagerField, level);
        if (entityManager == null) {
            return;
        }

        Field visibleEntityStorageField = ReflectionSupport.findField(entityManager.getClass(), "visibleEntityStorage", "e");
        if (visibleEntityStorageField == null) {
            return;
        }

        Object visibleEntityStorage = ReflectionSupport.readField(visibleEntityStorageField, entityManager);
        if (visibleEntityStorage == null) {
            return;
        }

        int entityId = resolveEntityId(oldHandle);
        Object uuid = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getUUID", "getUniqueID"}),
                oldHandle
        );

        replaceMapEntryByKey(visibleEntityStorage, "b", Integer.valueOf(entityId), oldHandle, replacementHandle);
        replaceMapEntryByKey(visibleEntityStorage, "c", uuid, oldHandle, replacementHandle);
    }

    private static void updateTrackedEntity(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Object trackedEntity = resolveEntityTracker(oldHandle);
        if (trackedEntity == null) {
            return;
        }

        Field trackerEntityField = ReflectionSupport.findField(trackedEntity.getClass(), "entity", "c");
        if (trackerEntityField != null) {
            ReflectionSupport.writeField(trackerEntityField, trackedEntity, replacementHandle);
        }

        Field serverEntityField = ReflectionSupport.findField(trackedEntity.getClass(), "serverEntity", "b");
        if (serverEntityField == null) {
            return;
        }

        Object serverEntity = ReflectionSupport.readField(serverEntityField, trackedEntity);
        if (serverEntity == null) {
            return;
        }

        Field serverEntityHandleField = ReflectionSupport.findField(serverEntity.getClass(), "entity", "d");
        if (serverEntityHandleField != null) {
            ReflectionSupport.writeField(serverEntityHandleField, serverEntity, replacementHandle);
        }
    }

    private static void rebindSectionManagerLevelCallback(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Field levelCallbackField = findEntityHandleField("levelCallback", "aO");
        if (levelCallbackField == null) {
            return;
        }

        Object oldLevelCallback = ReflectionSupport.readField(levelCallbackField, oldHandle);
        if (oldLevelCallback == null) {
            return;
        }

        migrateSectionMembership(oldLevelCallback, oldHandle, replacementHandle);

        Object replacementLevelCallback = retargetSectionCallback(oldLevelCallback, oldHandle, replacementHandle);
        if (replacementLevelCallback == null) {
            replacementLevelCallback = recreateSectionCallback(oldLevelCallback, replacementHandle);
        }
        if (replacementLevelCallback == null) {
            return;
        }

        Method setReplacementLevelCallbackMethod = ReflectionSupport.requireCompatibleMethod(
                replacementHandle.getClass(),
                new String[]{"setLevelCallback", "a"},
                replacementLevelCallback.getClass()
        );
        ReflectionSupport.invoke(setReplacementLevelCallbackMethod, replacementHandle, replacementLevelCallback);

        Class<?> entityInLevelCallbackType = ReflectionSupport.requireClass(
                "net.minecraft.world.level.entity.EntityInLevelCallback"
        );
        Object nullLevelCallback = ReflectionSupport.readField(
                ReflectionSupport.requireField(entityInLevelCallbackType, "NULL", "a"),
                null
        );
        Method clearOldLevelCallbackMethod = ReflectionSupport.requireCompatibleMethod(
                oldHandle.getClass(),
                new String[]{"setLevelCallback", "a"},
                nullLevelCallback.getClass()
        );
        ReflectionSupport.invoke(clearOldLevelCallbackMethod, oldHandle, nullLevelCallback);
    }

    private static @Nullable Object retargetSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (oldLevelCallback == null) {
            return null;
        }

        Field entityField = ReflectionSupport.findField(oldLevelCallback.getClass(), "entity", "c");
        if (entityField == null) {
            return null;
        }

        Object callbackEntity = ReflectionSupport.readField(entityField, oldLevelCallback);
        if (callbackEntity != oldHandle) {
            return null;
        }
        if (!entityField.getType().isAssignableFrom(replacementHandle.getClass())) {
            return null;
        }

        ReflectionSupport.writeField(entityField, oldLevelCallback, replacementHandle);
        return oldLevelCallback;
    }

    private static @Nullable Object recreateSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object replacementHandle
    ) {
        if (oldLevelCallback == null) {
            return null;
        }

        Field managerField = ReflectionSupport.findField(oldLevelCallback.getClass(), "this$0");
        Field currentSectionKeyField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSectionKey", "d");
        Field currentSectionField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSection", "e");
        if (managerField == null || currentSectionKeyField == null || currentSectionField == null) {
            return null;
        }

        Object manager = ReflectionSupport.readField(managerField, oldLevelCallback);
        Object currentSectionKey = ReflectionSupport.readField(currentSectionKeyField, oldLevelCallback);
        Object currentSection = ReflectionSupport.readField(currentSectionField, oldLevelCallback);
        if (!(currentSectionKey instanceof Long)) {
            return null;
        }

        Constructor<?> callbackConstructor = findSectionCallbackConstructor(
                oldLevelCallback.getClass(),
                manager,
                replacementHandle,
                currentSection
        );
        if (callbackConstructor == null) {
            return null;
        }

        return ReflectionSupport.instantiate(
                callbackConstructor,
                manager,
                replacementHandle,
                Long.valueOf(((Long) currentSectionKey).longValue()),
                currentSection
        );
    }

    private static @Nullable Constructor<?> findSectionCallbackConstructor(
            @NotNull Class<?> callbackType,
            @Nullable Object manager,
            @NotNull Object replacementHandle,
            @Nullable Object currentSection
    ) {
        for (Constructor<?> constructor : callbackType.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 4) {
                continue;
            }
            if (manager == null || !parameterTypes[0].isAssignableFrom(manager.getClass())) {
                continue;
            }
            if (!parameterTypes[1].isAssignableFrom(replacementHandle.getClass())) {
                continue;
            }
            if (!(parameterTypes[2] == long.class || parameterTypes[2] == Long.class)) {
                continue;
            }
            if (currentSection != null && !parameterTypes[3].isAssignableFrom(currentSection.getClass())) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor;
        }
        return null;
    }

    private static void migrateSectionMembership(
            @Nullable Object oldLevelCallback,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (oldLevelCallback == null) {
            return;
        }

        Field currentSectionField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSection", "e");
        if (currentSectionField == null) {
            return;
        }

        Object currentSection = ReflectionSupport.readField(currentSectionField, oldLevelCallback);
        if (currentSection == null) {
            return;
        }

        Method removeMethod = ReflectionSupport.findCompatibleMethod(
                currentSection.getClass(),
                new String[]{"remove", "b"},
                oldHandle.getClass()
        );
        Method addMethod = ReflectionSupport.findCompatibleMethod(
                currentSection.getClass(),
                new String[]{"add", "a"},
                replacementHandle.getClass()
        );
        if (removeMethod == null || addMethod == null) {
            return;
        }

        boolean removed = Boolean.TRUE.equals(ReflectionSupport.invoke(removeMethod, currentSection, oldHandle));
        if (removed || !containsManagedEntry(currentSection, replacementHandle)) {
            ReflectionSupport.invoke(addMethod, currentSection, replacementHandle);
        }
    }

    private static void replaceLifecycleCollections(
            @Nullable Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (level == null) {
            return;
        }

        Field entityTickListField = ReflectionSupport.findField(level.getClass(), "entityTickList", "F");
        if (entityTickListField != null) {
            Object entityTickList = ReflectionSupport.readField(entityTickListField, level);
            replaceEntityTickListEntries(entityTickList, resolveEntityId(oldHandle), oldHandle, replacementHandle);
        }

        Field navigatingMobsField = ReflectionSupport.findField(level.getClass(), "navigatingMobs", "M");
        if (navigatingMobsField != null) {
            Object navigatingMobs = ReflectionSupport.readField(navigatingMobsField, level);
            replaceManagedCollectionEntry(navigatingMobs, oldHandle, replacementHandle);
        }
    }

    private static void replaceEntityTickListEntries(
            @Nullable Object entityTickList,
            int entityId,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (entityTickList == null) {
            return;
        }
        replaceMapEntryByKey(entityTickList, "a", Integer.valueOf(entityId), oldHandle, replacementHandle);
        replaceMapEntryByKey(entityTickList, "b", Integer.valueOf(entityId), oldHandle, replacementHandle);
        replaceMapEntryByKey(entityTickList, "c", Integer.valueOf(entityId), oldHandle, replacementHandle);
    }

    private static void replaceManagedCollectionEntry(
            @Nullable Object collection,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        if (collection == null || !containsManagedEntry(collection, oldValue)) {
            return;
        }

        if (collection instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> values = (Collection<Object>) collection;
            if (values.remove(oldValue)) {
                values.add(newValue);
            }
            return;
        }

        Method removeMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"remove", "b"},
                oldValue.getClass()
        );
        Method addMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"add", "a"},
                newValue.getClass()
        );
        if (removeMethod != null && addMethod != null) {
            ReflectionSupport.invoke(removeMethod, collection, oldValue);
            ReflectionSupport.invoke(addMethod, collection, newValue);
        }
    }

    private static boolean containsManagedEntry(@Nullable Object collection, @NotNull Object value) {
        if (collection == null) {
            return false;
        }

        if (collection instanceof Collection) {
            return ((Collection<?>) collection).contains(value);
        }

        Method streamMethod = ReflectionSupport.findNamedMethod(collection.getClass(), new String[]{"getEntities", "b"});
        if (streamMethod == null) {
            return false;
        }

        Object values = ReflectionSupport.invoke(streamMethod, collection);
        if (values instanceof Stream) {
            try (Stream<?> stream = (Stream<?>) values) {
                return stream.anyMatch(candidate -> candidate == value);
            }
        }
        if (values instanceof Iterable) {
            for (Object candidate : (Iterable<?>) values) {
                if (candidate == value) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void replaceMapEntryByKey(
            @NotNull Object owner,
            @NotNull String fieldName,
            @Nullable Object key,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        if (key == null) {
            return;
        }

        Field field = ReflectionSupport.findField(owner.getClass(), fieldName);
        if (field == null) {
            return;
        }

        Object mapping = ReflectionSupport.readField(field, owner);
        if (!(mapping instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<Object, Object> values = (Map<Object, Object>) mapping;
        if (values.get(key) == oldValue) {
            values.put(key, newValue);
        }
    }

    private static @NotNull TrackedEntityState resolveTrackedEntityState(@NotNull Object nativeEntity) {
        Object trackedEntity = resolveEntityTracker(nativeEntity);
        if (trackedEntity == null) {
            return TrackedEntityState.untracked();
        }

        Field serverEntityField = ReflectionSupport.findField(trackedEntity.getClass(), "serverEntity", "b");
        Object serverEntity = serverEntityField == null ? null : ReflectionSupport.readField(serverEntityField, trackedEntity);
        return new TrackedEntityState(trackedEntity, serverEntity);
    }

    private static @Nullable Object resolveEntityTracker(@NotNull Object nativeEntity) {
        Field levelField = ReflectionSupport.findField(nativeEntity.getClass(), "level", "t");
        if (levelField == null) {
            return null;
        }

        Object level = ReflectionSupport.readField(levelField, nativeEntity);
        if (level == null) {
            return null;
        }

        Method getChunkProviderMethod = ReflectionSupport.findNamedMethod(level.getClass(), new String[]{"getChunkProvider"});
        if (getChunkProviderMethod == null) {
            return null;
        }

        Object chunkProvider = ReflectionSupport.invoke(getChunkProviderMethod, level);
        if (chunkProvider == null) {
            return null;
        }

        Field playerChunkMapField = ReflectionSupport.findField(chunkProvider.getClass(), "playerChunkMap", "a");
        if (playerChunkMapField == null) {
            return null;
        }

        Object playerChunkMap = ReflectionSupport.readField(playerChunkMapField, chunkProvider);
        if (playerChunkMap == null) {
            return null;
        }

        Field trackerMapField = ReflectionSupport.findField(playerChunkMap.getClass(), "trackedEntities", "G");
        if (trackerMapField == null) {
            return null;
        }

        Object trackerMap = ReflectionSupport.readField(trackerMapField, playerChunkMap);
        if (trackerMap == null) {
            return null;
        }

        int entityId = resolveEntityId(nativeEntity);
        if (trackerMap instanceof Map) {
            return ((Map<?, ?>) trackerMap).get(Integer.valueOf(entityId));
        }

        Method getMethod = ReflectionSupport.findCompatibleMethod(trackerMap.getClass(), new String[]{"get"}, int.class);
        if (getMethod == null) {
            return null;
        }
        return ReflectionSupport.invoke(getMethod, trackerMap, Integer.valueOf(entityId));
    }

    private static int resolveEntityId(@NotNull Object nativeEntity) {
        return ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(nativeEntity.getClass(), new String[]{"getId"}),
                nativeEntity
        )).intValue();
    }

    private static void rewireModernVehicleAndPassengerReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field passengersField = requireEntityHandleField("passengers", "at");
        Field vehicleField = requireEntityHandleField("vehicle", "au");

        @SuppressWarnings("unchecked")
        List<Object> passengers = (List<Object>) ReflectionSupport.readField(passengersField, oldHandle);
        if (passengers != null) {
            for (Object passenger : passengers) {
                ReflectionSupport.writeField(vehicleField, passenger, replacementHandle);
            }
        }

        Object vehicle = ReflectionSupport.readField(vehicleField, oldHandle);
        if (vehicle != null) {
            @SuppressWarnings("unchecked")
            List<Object> vehiclePassengers = (List<Object>) ReflectionSupport.readField(passengersField, vehicle);
            if (vehiclePassengers != null) {
                List<Object> replacedPassengers = new ArrayList<Object>(vehiclePassengers.size());
                for (Object passenger : vehiclePassengers) {
                    replacedPassengers.add(passenger == oldHandle ? replacementHandle : passenger);
                }
                ReflectionSupport.writeField(passengersField, vehicle, immutablePassengerList(replacedPassengers));
            }
        }
    }

    private static @NotNull Object immutablePassengerList(@NotNull List<Object> passengers) {
        Class<?> immutableListClass = ReflectionSupport.requireClass("com.google.common.collect.ImmutableList");
        Method copyOfMethod = ReflectionSupport.requireCompatibleMethod(
                immutableListClass,
                new String[]{"copyOf"},
                java.util.Collection.class
        );
        return ReflectionSupport.invoke(copyOfMethod, null, passengers);
    }

    private static void refreshModernBukkitWrappersInternal(@NotNull Entity entity) {
        Field equipmentField = ReflectionSupport.findField(entity.getClass(), "equipment");
        if (equipmentField != null) {
            ReflectionSupport.writeField(equipmentField, entity, null);
        }
    }

    private static void markModernEntityRemovedInternal(@NotNull Object oldHandle) {
        Class<?> removalReasonType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity$RemovalReason");
        Object discarded = ReflectionSupport.readField(
                ReflectionSupport.requireField(removalReasonType, "DISCARDED", "c"),
                null
        );
        Method setRemovedMethod = ReflectionSupport.requireCompatibleMethod(
                oldHandle.getClass(),
                new String[]{"setRemoved", "a"},
                removalReasonType
        );
        ReflectionSupport.invoke(setRemovedMethod, oldHandle, discarded);
        Field validField = findEntityHandleField("valid");
        if (validField != null) {
            ReflectionSupport.writeField(validField, oldHandle, Boolean.FALSE);
        }
    }

    private static @NotNull Field requireEntityHandleField(@NotNull String... candidateNames) {
        return ReflectionSupport.requireField(entityHandleClass(), candidateNames);
    }

    private static @Nullable Field findEntityHandleField(@NotNull String... candidateNames) {
        return ReflectionSupport.findField(entityHandleClass(), candidateNames);
    }

    private static @NotNull Class<?> entityHandleClass() {
        return ReflectionSupport.requireClass("net.minecraft.world.entity.Entity");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull Class<T> entityType(@NotNull T entity) {
        return (Class<T>) entity.getClass().asSubclass(Entity.class);
    }

    private <T extends Entity> @NotNull GoalProfile<T> snapshotManagedGoals(
            @NotNull Class<T> entityType,
            @NotNull Object nativeHandle
    ) {
        GoalProfile.Builder<T> builder = GoalProfile.builder(entityType);
        for (GoalSelectorType selectorType : GoalSelectorType.values()) {
            SelectorAccessor selectorAccessor = SelectorAccessor.resolve(nativeHandle, selectorType);
            for (Object selectorEntry : selectorAccessor.entries()) {
                ManagedGoalEntry entry = ManagedGoalEntry.resolve(selectorEntry);
                if (entry == null) {
                    continue;
                }
                if (entry.vanillaKey() != null) {
                    builder.add(VanillaGoalSpec.of(selectorType, entry.vanillaKey(), entry.priority()));
                    continue;
                }
                if (entry.customKey() != null) {
                    builder.add(CustomGoalSpec.of(selectorType, entry.customKey(), entry.priority()));
                }
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

    private void schedulePaperReplacementRepairPass(
            @NotNull NativeEntityLifecycle<Entity> lifecycle,
            @NotNull Entity entity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        requireRuntimeLifecycle(lifecycle).scheduleNextTickRepair(new Runnable() {
            @Override
            public void run() {
                rebindBukkitZombie(entity, replacementHandle);
                rebindModernBukkitBridge(entity, oldHandle, replacementHandle);
                replaceModernWorldReferences(oldHandle, replacementHandle);
                rewireModernVehicleAndPassengerReferences(oldHandle, replacementHandle);
                refreshModernBukkitWrappers(entity);
            }
        });
    }

    private static @NotNull AbstractRuntimeControlledEntity<?> requireRuntimeLifecycle(
            @NotNull NativeEntityLifecycle<?> lifecycle
    ) {
        ControlledEntity<?> handle = lifecycle.handle();
        if (!(handle instanceof AbstractRuntimeControlledEntity)) {
            throw new IllegalStateException(
                    "The runtime lifecycle handle must extend AbstractRuntimeControlledEntity for entity attachment."
            );
        }
        return (AbstractRuntimeControlledEntity<?>) handle;
    }

    private static @NotNull UnsupportedOperationException unsupported(@NotNull String operation) {
        return new UnsupportedOperationException(
                "Minecraft " + SUPPORTED_FAMILY + " skeleton does not implement native " + operation + " yet (anchor " + VERSION + ")."
        );
    }

    private static @NotNull UnsupportedOperationException unsupportedPublicationFamily(
            @NotNull EntityPublicationFamily family,
            @NotNull String operation
    ) {
        return new UnsupportedOperationException(
                "Minecraft "
                        + SUPPORTED_FAMILY
                        + " skeleton does not support publication family '"
                        + family.id()
                        + "' for native "
                        + operation
                        + " yet (anchor "
                        + VERSION
                        + ")."
        );
    }

    private static final class GoalMutationExecutorBridge<T extends Entity> implements RuntimeGoalMutationExecutor<T> {
        private final EntityFactoryV1_17_1 factory;
        private final Class<T> entityType;
        private final GoalProfile<T> initialManagedGoals;

        private GoalMutationExecutorBridge(
                @NotNull EntityFactoryV1_17_1 factory,
                @NotNull Class<T> entityType,
                @NotNull GoalProfile<T> initialManagedGoals
        ) {
            this.factory = Objects.requireNonNull(factory, "factory cannot be null");
            this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null");
            this.initialManagedGoals = Objects.requireNonNull(initialManagedGoals, "initialManagedGoals cannot be null");
        }

        @Override
        public @NotNull GoalProfile<T> initialManagedGoals() {
            return initialManagedGoals;
        }

        @Override
        public void execute(@NotNull RuntimeGoalMutationBatch<T> batch) {
            Objects.requireNonNull(batch, "batch cannot be null");
            try {
                applyManagedGoalSnapshot(
                        factory.resolveCurrentNativeHandle(batch.controlledEntity().bukkitEntity()),
                        batch.managedGoals()
                );
            } catch (GoalOperationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new GoalOperationException(
                        "Minecraft " + VERSION + " failed to apply managed goals for '" + entityType.getName() + "'.",
                        exception,
                        GoalSelectorType.NORMAL,
                        entityType,
                        VERSION
                );
            }
        }
    }

    private static final class SelectorAccessor {
        private final Object owner;
        private final GoalSelectorType selectorType;
        private final Object selector;
        private final Field availableGoalsField;
        private final Method addGoalMethod;

        private SelectorAccessor(
                @NotNull Object owner,
                @NotNull GoalSelectorType selectorType,
                @NotNull Object selector,
                @NotNull Field availableGoalsField,
                @NotNull Method addGoalMethod
        ) {
            this.owner = owner;
            this.selectorType = selectorType;
            this.selector = selector;
            this.availableGoalsField = availableGoalsField;
            this.addGoalMethod = addGoalMethod;
        }

        private static @NotNull SelectorAccessor resolve(@NotNull Object owner, @NotNull GoalSelectorType selectorType) {
            Field selectorField = selectorType == GoalSelectorType.NORMAL
                    ? ReflectionSupport.findField(owner.getClass(), "goalSelector", "bP", "bQ")
                    : ReflectionSupport.findField(owner.getClass(), "targetSelector", "bQ", "bR");
            if (selectorField == null) {
                throw unsupportedSelector(selectorType, null);
            }
            Object selector = ReflectionSupport.readField(selectorField, owner);
            if (selector == null) {
                throw unsupportedSelector(selectorType, null);
            }
            Field availableGoalsField = ReflectionSupport.findField(selector.getClass(), "availableGoals", "d", "c", "goals");
            if (availableGoalsField == null) {
                throw unsupportedSelector(selectorType, null);
            }
            Method addGoalMethod = findAddGoalMethod(selector.getClass());
            if (addGoalMethod == null) {
                throw unsupportedSelector(selectorType, null);
            }
            return new SelectorAccessor(owner, selectorType, selector, availableGoalsField, addGoalMethod);
        }

        @SuppressWarnings("unchecked")
        private @NotNull Collection<Object> entries() {
            Object entries = ReflectionSupport.readField(availableGoalsField, selector);
            if (!(entries instanceof Collection)) {
                throw unsupportedSelector(selectorType, null);
            }
            return (Collection<Object>) entries;
        }

        private void removeManagedEntries() {
            for (Iterator<Object> iterator = entries().iterator(); iterator.hasNext(); ) {
                if (ManagedGoalEntry.resolve(iterator.next()) != null) {
                    iterator.remove();
                }
            }
        }

        private void add(int priority, @NotNull Object goal) {
            Object resolvedGoal = goal;
            Class<?> goalParameterType = addGoalMethod.getParameterTypes()[1];
            if (!goalParameterType.isInstance(resolvedGoal)) {
                resolvedGoal = createNativeGoal(goalParameterType, goal);
            }
            if (!goalParameterType.isInstance(resolvedGoal)) {
                throw new GoalOperationException(
                        "Minecraft " + VERSION + " could not materialize a compatible goal bridge.",
                        selectorType,
                        null,
                        VERSION
                );
            }
            ReflectionSupport.invoke(addGoalMethod, selector, Integer.valueOf(priority), resolvedGoal);
        }

        private @NotNull Object createVanillaGoal(@NotNull VanillaGoalSpec goalSpec) {
            return new ManagedVanillaGoalBridge(goalSpec.key());
        }

        private @NotNull Object createNativeGoal(@NotNull Class<?> goalParameterType, @NotNull Object goal) {
            if (goal instanceof ManagedVanillaGoalBridge) {
                Object nativeGoal = tryCreateNativeVanillaGoal(goalParameterType, ((ManagedVanillaGoalBridge) goal).key());
                if (goalParameterType.isInstance(nativeGoal)) {
                    return nativeGoal;
                }
            }
            return goal;
        }

        private @NotNull Object tryCreateNativeVanillaGoal(
                @NotNull Class<?> goalParameterType,
                @NotNull VanillaGoalKey key
        ) {
            try {
                switch (key) {
                    case FLOAT:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.FloatGoal", owner);
                    case MELEE_ATTACK:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.MeleeAttackGoal", owner, Double.valueOf(1.0D), Boolean.TRUE);
                    case RANDOM_STROLL_LAND:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.RandomStrollGoal", owner, Double.valueOf(1.0D));
                    case LOOK_AT_PLAYER:
                        return instantiateGoal(
                                "net.minecraft.world.entity.ai.goal.LookAtPlayerGoal",
                                owner,
                                ReflectionSupport.requireClass("net.minecraft.world.entity.player.Player"),
                                Float.valueOf(8.0F)
                        );
                    case RANDOM_LOOK_AROUND:
                        return instantiateGoal("net.minecraft.world.entity.ai.goal.RandomLookAroundGoal", owner);
                    case HURT_BY_TARGET:
                        return instantiateGoal(
                                "net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal",
                                owner,
                                new Class[0]
                        );
                    case NEAREST_ATTACKABLE_TARGET:
                        return instantiateGoal(
                                "net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal",
                                owner,
                                ReflectionSupport.requireClass("net.minecraft.world.entity.player.Player"),
                                Boolean.TRUE
                        );
                    default:
                        return new ManagedVanillaGoalBridge(key);
                }
            } catch (RuntimeException ignored) {
                return new ManagedVanillaGoalBridge(key);
            }
        }

        private static @NotNull Object instantiateGoal(@NotNull String goalClassName, @NotNull Object... arguments) {
            Class<?> goalType = ReflectionSupport.requireClass(goalClassName);
            Class<?>[] argumentTypes = new Class<?>[arguments.length];
            for (int index = 0; index < arguments.length; index++) {
                argumentTypes[index] = arguments[index].getClass();
                if (arguments[index] instanceof Class[]) {
                    argumentTypes[index] = Class[].class;
                }
            }
            Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(goalType, argumentTypes);
            return ReflectionSupport.instantiate(constructor, arguments);
        }

        private static @Nullable Method findAddGoalMethod(@NotNull Class<?> selectorType) {
            Class<?> current = selectorType;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == 2
                            && (Integer.TYPE.equals(parameterTypes[0]) || Integer.class.equals(parameterTypes[0]))
                            && ("addGoal".equals(method.getName()) || "a".equals(method.getName()))) {
                        method.setAccessible(true);
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
            VanillaGoalKey key = resolveVanillaKey(goal.getClass());
            if (key == null) {
                return null;
            }
            return new ManagedGoalEntry(key, null, resolvePriority(selectorEntry));
        }

        private static @NotNull Object resolveGoal(@NotNull Object selectorEntry) {
            Method goalMethod = ReflectionSupport.findNamedMethod(selectorEntry.getClass(), new String[]{"getGoal", "j", "k"});
            if (goalMethod != null) {
                return ReflectionSupport.invoke(goalMethod, selectorEntry);
            }
            Field goalField = ReflectionSupport.findField(selectorEntry.getClass(), "goal", "c", "a", "b");
            if (goalField != null) {
                Object goal = ReflectionSupport.readField(goalField, selectorEntry);
                if (goal != null) {
                    return goal;
                }
            }
            return selectorEntry;
        }

        private static int resolvePriority(@NotNull Object selectorEntry) {
            Method priorityMethod = ReflectionSupport.findNamedMethod(selectorEntry.getClass(), new String[]{"getPriority", "i", "h"});
            if (priorityMethod != null) {
                Object priority = ReflectionSupport.invoke(priorityMethod, selectorEntry);
                if (priority instanceof Number) {
                    return ((Number) priority).intValue();
                }
            }
            Field priorityField = ReflectionSupport.findField(selectorEntry.getClass(), "priority", "d", "b", "a");
            if (priorityField != null) {
                Object priority = ReflectionSupport.readField(priorityField, selectorEntry);
                if (priority instanceof Number) {
                    return ((Number) priority).intValue();
                }
            }
            return 0;
        }

        private static @Nullable VanillaGoalKey resolveVanillaKey(@NotNull Class<?> goalType) {
            String simpleName = goalType.getSimpleName();
            if ("FloatGoal".equals(simpleName)) {
                return VanillaGoalKey.FLOAT;
            }
            if ("MeleeAttackGoal".equals(simpleName)) {
                return VanillaGoalKey.MELEE_ATTACK;
            }
            if ("RandomStrollGoal".equals(simpleName) || "RandomStrollLandGoal".equals(simpleName)) {
                return VanillaGoalKey.RANDOM_STROLL_LAND;
            }
            if ("LookAtPlayerGoal".equals(simpleName)) {
                return VanillaGoalKey.LOOK_AT_PLAYER;
            }
            if ("RandomLookAroundGoal".equals(simpleName)) {
                return VanillaGoalKey.RANDOM_LOOK_AROUND;
            }
            if ("HurtByTargetGoal".equals(simpleName)) {
                return VanillaGoalKey.HURT_BY_TARGET;
            }
            if ("NearestAttackableTargetGoal".equals(simpleName)) {
                return VanillaGoalKey.NEAREST_ATTACKABLE_TARGET;
            }
            return null;
        }

        private @Nullable VanillaGoalKey vanillaKey() {
            return vanillaKey;
        }

        private @Nullable CustomGoalKey customKey() {
            return customKey;
        }

        private int priority() {
            return priority;
        }
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

    private static @NotNull UnsupportedGoalOperationException unsupportedSelector(
            @NotNull GoalSelectorType selectorType,
            @Nullable Class<? extends Entity> entityType
    ) {
        return new UnsupportedGoalOperationException(
                "Minecraft " + VERSION + " does not expose a compatible managed-goal selector bridge.",
                selectorType,
                entityType,
                VERSION
        );
    }

    private static final class EntityMetadata {
        private final CustomEntityBaseType baseType;
        private final EntityType entityType;

        private EntityMetadata(@NotNull CustomEntityBaseType baseType, @NotNull EntityType entityType) {
            this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
            this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null");
        }

        public @NotNull CustomEntityBaseType baseType() {
            return baseType;
        }

        public @NotNull EntityType entityType() {
            return entityType;
        }
    }

    private static final class ResolvedEntityTypeMetadata {
        private final EntityMetadata metadata;
        private final Class<?> nativeType;
        private final Class<?> generatedType;
        private final Collection<GeneratedNativeHookSpec> hookSpecs;

        private ResolvedEntityTypeMetadata(
                @NotNull EntityMetadata metadata,
                @NotNull Class<?> nativeType,
                @NotNull Class<?> generatedType,
                @NotNull Collection<GeneratedNativeHookSpec> hookSpecs
        ) {
            this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
            this.nativeType = Objects.requireNonNull(nativeType, "nativeType cannot be null");
            this.generatedType = Objects.requireNonNull(generatedType, "generatedType cannot be null");
            this.hookSpecs = Objects.requireNonNull(hookSpecs, "hookSpecs cannot be null");
        }

        public @NotNull CustomEntityBaseType baseType() {
            return metadata.baseType();
        }

        public @NotNull Class<?> nativeType() {
            return nativeType;
        }

        public @NotNull Class<?> generatedType() {
            return generatedType;
        }

        public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs() {
            return hookSpecs;
        }
    }

    private static final class ResolvedSpawnMetadata {
        private final ResolvedEntityTypeMetadata resolvedMetadata;
        private final Object nativeEntityType;

        private ResolvedSpawnMetadata(
                @NotNull ResolvedEntityTypeMetadata resolvedMetadata,
                @Nullable Object nativeEntityType
        ) {
            this.resolvedMetadata = Objects.requireNonNull(resolvedMetadata, "resolvedMetadata cannot be null");
            this.nativeEntityType = nativeEntityType;
        }

        public @NotNull ResolvedEntityTypeMetadata resolvedMetadata() {
            return resolvedMetadata;
        }

        public @Nullable Object nativeEntityType() {
            return nativeEntityType;
        }
    }

    private static final class ReplacementMetadata {
        private final Class<?> replacementType;

        private ReplacementMetadata(@NotNull Class<?> replacementType) {
            this.replacementType = Objects.requireNonNull(replacementType, "replacementType cannot be null");
        }

        public @NotNull Class<?> replacementType() {
            return replacementType;
        }
    }

    private static final class TrackedEntityState {
        private static final TrackedEntityState UNTRACKED = new TrackedEntityState(null, null);

        private final Object trackedEntity;
        private final Object serverEntity;

        private TrackedEntityState(@Nullable Object trackedEntity, @Nullable Object serverEntity) {
            this.trackedEntity = trackedEntity;
            this.serverEntity = serverEntity;
        }

        public static @NotNull TrackedEntityState untracked() {
            return UNTRACKED;
        }

        public @Nullable Object trackedEntity() {
            return trackedEntity;
        }

        public @Nullable Object serverEntity() {
            return serverEntity;
        }
    }
}
