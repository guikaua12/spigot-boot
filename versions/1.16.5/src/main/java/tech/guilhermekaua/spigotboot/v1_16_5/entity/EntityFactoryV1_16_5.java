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
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Version-local spawn and attach scaffold for the Minecraft 1.14-1.16.5 family.
 *
 * @since 2.0.2
 */
public final class EntityFactoryV1_16_5
        implements VersionEntrypoint,
        PaperTrackingBindingStrategy_1_21_plus.Support,
        PaperFreshSpawnStrategy_1_21_plus.Support,
        PaperReplacementStrategy_1_21_plus.Support {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 16, 5);
    private static final String SUPPORTED_FAMILY = "1.14-1.16.5";
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
    private static final GeneratedNativeEntityClassFactory CLASS_FACTORY = new GeneratedNativeEntityClassFactory();
    private static final EntityHookBinderV1_16_5 HOOK_BINDER = new EntityHookBinderV1_16_5();
    private static final Map<Class<?>, ResolvedEntityTypeMetadata> GENERATED_ENTITY_TYPES =
            new LinkedHashMap<Class<?>, ResolvedEntityTypeMetadata>();
    private static final Class<?> NMS_ENTITY_CLASS = resolveNmsEntityClass();

    private final Map<UUID, ControlledEntity<?>> attachedEntities = new LinkedHashMap<UUID, ControlledEntity<?>>();
    private final Map<CustomEntityBaseType, EntityMetadata> metadataRegistry = createMetadataRegistry();
    private final Map<CustomEntityBaseType, ResolvedSpawnMetadata> spawnMetadataRegistry =
            new LinkedHashMap<CustomEntityBaseType, ResolvedSpawnMetadata>();

    public static @NotNull VersionCapabilities entityCapabilities() {
        return ENTITY_CAPABILITIES;
    }

    public static @NotNull VersionBindings entityBindings() {
        return ENTITY_BINDINGS;
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
        return metadataRegistry.containsKey(baseType);
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
        requireSupportedBaseType(resolveBaseType(entity));
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
        Objects.requireNonNull(family, "family cannot be null");
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        if (family != EntityPublicationFamily.ENTITIES_BY_UUID) {
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
        return "v1_16_5_"
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
            Method getTypeMethod = ReflectionSupport.findNamedMethod(
                    probeHandle.getClass(),
                    new String[]{"getType", "X", "getEntityType"}
            );
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
                new String[]{"moveTo", "setPositionRotation", "setLocation"},
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
                new String[]{"setPos", "setPosition", "d"},
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

        Method setRotMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"setRot", "setYawPitch", "b"},
                float.class,
                float.class
        );
        if (setRotMethod != null) {
            ReflectionSupport.invoke(
                    setRotMethod,
                    nativeEntity,
                    Float.valueOf(location.getYaw()),
                    Float.valueOf(location.getPitch())
            );
            return;
        }

        Method setYRotMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"setYRot"},
                float.class
        );
        if (setYRotMethod != null) {
            ReflectionSupport.invoke(setYRotMethod, nativeEntity, Float.valueOf(location.getYaw()));
        }

        Method setXRotMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"setXRot"},
                float.class
        );
        if (setXRotMethod != null) {
            ReflectionSupport.invoke(setXRotMethod, nativeEntity, Float.valueOf(location.getPitch()));
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
        metadata.put(CustomEntityBaseType.ZOMBIE, new EntityMetadata(CustomEntityBaseType.ZOMBIE, EntityType.ZOMBIE));
        return metadata;
    }

    private static @NotNull Entity spawnVanillaEntity(
            @NotNull Location location,
            @NotNull EntityMetadata metadata
    ) {
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        return world.spawnEntity(location.clone(), metadata.entityType());
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

    private static void requireSupportedBaseType(@NotNull CustomEntityBaseType baseType) {
        if (baseType != CustomEntityBaseType.ZOMBIE) {
            throw new UnsupportedOperationException(
                    "Minecraft " + SUPPORTED_FAMILY + " does not support attach for base type '" + baseType + "'."
            );
        }
    }

    private static @NotNull Object resolveNativeHandle(@NotNull Entity entity) {
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(entity.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, entity);
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
        Field bukkitEntityField = findFirstField(new Object[]{replacementHandle, oldHandle}, "bukkitEntity");
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
                requireEntityHandleField(oldHandle, "level", "l", "world"),
                oldHandle
        );
        if (level == null) {
            return;
        }

        replaceMapEntryByKey(
                level,
                Integer.valueOf(resolveEntityId(oldHandle)),
                oldHandle,
                replacementHandle,
                "entitiesById",
                "y"
        );
        replaceMapEntryByKey(
                level,
                resolveEntityUuid(oldHandle),
                oldHandle,
                replacementHandle,
                "entitiesByUuid",
                "entitiesByUUID",
                "z"
        );
        updateTrackedEntity(oldHandle, replacementHandle);
        migrateChunkSectionMembership(level, oldHandle, replacementHandle);
    }

    private static void updateTrackedEntity(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Object trackedEntity = resolveEntityTracker(oldHandle);
        if (trackedEntity == null) {
            return;
        }

        Field trackerEntityField = ReflectionSupport.findField(
                trackedEntity.getClass(),
                "entity",
                "c",
                "tracker"
        );
        if (trackerEntityField != null) {
            ReflectionSupport.writeField(trackerEntityField, trackedEntity, replacementHandle);
        }

        Field serverEntityField = ReflectionSupport.findField(
                trackedEntity.getClass(),
                "serverEntity",
                "b",
                "entry",
                "trackerEntry"
        );
        if (serverEntityField == null) {
            return;
        }

        Object serverEntity = ReflectionSupport.readField(serverEntityField, trackedEntity);
        if (serverEntity == null) {
            return;
        }

        Field serverEntityHandleField = ReflectionSupport.findField(
                serverEntity.getClass(),
                "entity",
                "c",
                "tracker"
        );
        if (serverEntityHandleField != null) {
            ReflectionSupport.writeField(serverEntityHandleField, serverEntity, replacementHandle);
        }
    }

    private static void migrateChunkSectionMembership(
            @Nullable Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (level == null || !isEntityMarkedInChunk(oldHandle)) {
            return;
        }

        Object chunkSource = resolveChunkSource(level);
        if (chunkSource == null) {
            return;
        }

        Object chunk = resolveEntityChunk(chunkSource, oldHandle);
        if (chunk == null) {
            return;
        }

        Collection<Object> section = resolveChunkSectionEntities(chunk, oldHandle);
        if (section == null) {
            return;
        }

        boolean removed = section.remove(oldHandle);
        if (removed || !section.contains(replacementHandle)) {
            section.add(replacementHandle);
        }
    }

    private static void replaceMapEntryByKey(
            @NotNull Object owner,
            @Nullable Object key,
            @NotNull Object oldValue,
            @NotNull Object newValue,
            @NotNull String... fieldNames
    ) {
        if (key == null) {
            return;
        }

        Field field = ReflectionSupport.findField(owner.getClass(), fieldNames);
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

        Field serverEntityField = ReflectionSupport.findField(
                trackedEntity.getClass(),
                "serverEntity",
                "b",
                "entry",
                "trackerEntry"
        );
        Object serverEntity = serverEntityField == null ? null : ReflectionSupport.readField(serverEntityField, trackedEntity);
        return new TrackedEntityState(trackedEntity, serverEntity);
    }

    private static @Nullable Object resolveEntityTracker(@NotNull Object nativeEntity) {
        Object level = ReflectionSupport.readField(
                requireEntityHandleField(nativeEntity, "level", "l", "world"),
                nativeEntity
        );
        if (level == null) {
            return null;
        }

        Object chunkSource = resolveChunkSource(level);
        if (chunkSource == null) {
            return null;
        }

        Object chunkMap = resolveChunkMap(chunkSource);
        if (chunkMap == null) {
            return null;
        }

        Field trackerMapField = ReflectionSupport.findField(
                chunkMap.getClass(),
                "entityMap",
                "y",
                "trackedEntities",
                "entityTrackers"
        );
        if (trackerMapField == null) {
            return null;
        }

        Object trackerMap = ReflectionSupport.readField(trackerMapField, chunkMap);
        if (!(trackerMap instanceof Map)) {
            return null;
        }

        return ((Map<?, ?>) trackerMap).get(Integer.valueOf(resolveEntityId(nativeEntity)));
    }

    private static @Nullable Object resolveChunkSource(@Nullable Object level) {
        if (level == null) {
            return null;
        }

        Method chunkSourceMethod = ReflectionSupport.findNamedMethod(
                level.getClass(),
                new String[]{"getChunkSource", "getChunkProvider", "getChunkProviderServer"}
        );
        if (chunkSourceMethod != null) {
            return ReflectionSupport.invoke(chunkSourceMethod, level);
        }

        Field chunkSourceField = ReflectionSupport.findField(level.getClass(), "chunkSource", "C", "chunkProvider");
        return chunkSourceField == null ? null : ReflectionSupport.readField(chunkSourceField, level);
    }

    private static @Nullable Object resolveChunkMap(@Nullable Object chunkSource) {
        if (chunkSource == null) {
            return null;
        }

        Field chunkMapField = ReflectionSupport.findField(
                chunkSource.getClass(),
                "chunkMap",
                "a",
                "playerChunkMap",
                "threadedAnvilChunkStorage"
        );
        return chunkMapField == null ? null : ReflectionSupport.readField(chunkMapField, chunkSource);
    }

    private static @Nullable Object resolveEntityChunk(@NotNull Object chunkSource, @NotNull Object entityHandle) {
        int chunkX = resolveChunkCoordinate(entityHandle, "xChunk", "V", "chunkX");
        int chunkZ = resolveChunkCoordinate(entityHandle, "zChunk", "X", "chunkZ");

        Method getChunkMethod = ReflectionSupport.findCompatibleMethod(
                chunkSource.getClass(),
                new String[]{"getChunkNow", "getWorldChunk", "getChunk", "a", "c"},
                int.class,
                int.class
        );
        if (getChunkMethod == null) {
            return null;
        }
        return ReflectionSupport.invoke(getChunkMethod, chunkSource, Integer.valueOf(chunkX), Integer.valueOf(chunkZ));
    }

    private static @Nullable Collection<Object> resolveChunkSectionEntities(@NotNull Object chunk, @NotNull Object entityHandle) {
        Field entitySectionsField = ReflectionSupport.findField(chunk.getClass(), "entitySections", "k", "entitySlices");
        if (entitySectionsField == null) {
            return null;
        }

        Object entitySections = ReflectionSupport.readField(entitySectionsField, chunk);
        if (!(entitySections instanceof Object[])) {
            return null;
        }

        Object[] sections = (Object[]) entitySections;
        if (sections.length == 0) {
            return null;
        }

        int sectionIndex = resolveChunkCoordinate(entityHandle, "yChunk", "W", "chunkY");
        if (sectionIndex < 0) {
            sectionIndex = 0;
        } else if (sectionIndex >= sections.length) {
            sectionIndex = sections.length - 1;
        }

        Object section = sections[sectionIndex];
        if (!(section instanceof Collection)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Collection<Object> entities = (Collection<Object>) section;
        return entities;
    }

    private static boolean isEntityMarkedInChunk(@NotNull Object entityHandle) {
        Field inChunkField = findEntityHandleField(entityHandle, "inChunk", "U", "updateNeeded");
        if (inChunkField == null) {
            return true;
        }

        Object value = ReflectionSupport.readField(inChunkField, entityHandle);
        return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
    }

    private static int resolveChunkCoordinate(@NotNull Object entityHandle, @NotNull String... candidateNames) {
        Field coordinateField = findEntityHandleField(entityHandle, candidateNames);
        if (coordinateField == null) {
            return 0;
        }

        Object coordinate = ReflectionSupport.readField(coordinateField, entityHandle);
        return coordinate instanceof Number ? ((Number) coordinate).intValue() : 0;
    }

    private static int resolveEntityId(@NotNull Object nativeEntity) {
        Method getIdMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"getId", "Y", "getEntityId"});
        if (getIdMethod != null) {
            Object entityId = ReflectionSupport.invoke(getIdMethod, nativeEntity);
            if (entityId instanceof Number) {
                return ((Number) entityId).intValue();
            }
        }

        Field idField = ReflectionSupport.findField(nativeEntity.getClass(), "id", "g", "entityId");
        if (idField != null) {
            Object entityId = ReflectionSupport.readField(idField, nativeEntity);
            if (entityId instanceof Number) {
                return ((Number) entityId).intValue();
            }
        }

        throw new IllegalStateException(
                "Could not resolve an entity id from native type '" + nativeEntity.getClass().getName() + "'."
        );
    }

    private static @Nullable Object resolveEntityUuid(@NotNull Object nativeEntity) {
        Method uuidMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"getUUID", "getUniqueID"});
        if (uuidMethod != null) {
            return ReflectionSupport.invoke(uuidMethod, nativeEntity);
        }

        Field uuidField = ReflectionSupport.findField(nativeEntity.getClass(), "uuid", "ad", "uniqueID");
        return uuidField == null ? null : ReflectionSupport.readField(uuidField, nativeEntity);
    }

    private static void rewireModernVehicleAndPassengerReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        // resolve the passengers field by its declared type to skip the static DataWatcherObject
        // shadowing "ag" on EntityLiving on v1_16_R3 (and any equivalent obfuscation collisions).
        Field passengersField = ReflectionSupport.requireFieldOfType(
                oldHandle.getClass(), List.class, "passengers", "ag", "passengerList");
        // filter the vehicle field by the NMS Entity type when available; in unit-test environments
        // where the NMS jar is absent the filter falls back to Object.class — non-restrictive but harmless.
        Class<?> vehicleType = (NMS_ENTITY_CLASS != null) ? NMS_ENTITY_CLASS : Object.class;
        Field vehicleField = ReflectionSupport.requireFieldOfType(
                oldHandle.getClass(), vehicleType, "vehicle", "ah");

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
                ReflectionSupport.writeField(passengersField, vehicle, replacedPassengers);
            }
        }
    }

    private static void refreshModernBukkitWrappersInternal(@NotNull Entity entity) {
        Field equipmentField = ReflectionSupport.findField(entity.getClass(), "equipment");
        if (equipmentField != null) {
            ReflectionSupport.writeField(equipmentField, entity, null);
        }
    }

    private static void markModernEntityRemovedInternal(@NotNull Object oldHandle) {
        Field removedField = ReflectionSupport.findField(oldHandle.getClass(), "removed", "y", "dead");
        if (removedField != null) {
            ReflectionSupport.writeField(removedField, oldHandle, Boolean.TRUE);
        }

        Field validField = ReflectionSupport.findField(oldHandle.getClass(), "valid");
        if (validField != null) {
            ReflectionSupport.writeField(validField, oldHandle, Boolean.FALSE);
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

    private static @NotNull Field requireEntityHandleField(@NotNull Object entityHandle, @NotNull String... candidateNames) {
        return ReflectionSupport.requireField(entityHandle.getClass(), candidateNames);
    }

    private static @Nullable Field findEntityHandleField(@NotNull Object entityHandle, @NotNull String... candidateNames) {
        return ReflectionSupport.findField(entityHandle.getClass(), candidateNames);
    }

    private static @Nullable Class<?> resolveNmsEntityClass() {
        try {
            return Class.forName("net.minecraft.server.v1_16_R3.Entity");
        } catch (ClassNotFoundException exception) {
            // tolerated: unit tests do not include the NMS jar on the classpath
            return null;
        }
    }

    private static @Nullable Field findFirstField(@NotNull Object[] handles, @NotNull String... candidateNames) {
        for (Object handle : handles) {
            if (handle == null) {
                continue;
            }
            Field field = ReflectionSupport.findField(handle.getClass(), candidateNames);
            if (field != null) {
                return field;
            }
        }
        return null;
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

    private static final class ReplacementMetadata {
        private final Class<?> replacementType;

        private ReplacementMetadata(@NotNull Class<?> replacementType) {
            this.replacementType = Objects.requireNonNull(replacementType, "replacementType cannot be null");
        }

        public @NotNull Class<?> replacementType() {
            return replacementType;
        }
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

        public @NotNull EntityMetadata metadata() {
            return metadata;
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
