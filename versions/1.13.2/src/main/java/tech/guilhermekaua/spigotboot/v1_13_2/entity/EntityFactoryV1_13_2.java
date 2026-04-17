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
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.VersionEntrypoint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Version-local spawn and attach scaffold for the Minecraft 1.13-1.13.2 family.
 *
 * @since 2.0.2
 */
public final class EntityFactoryV1_13_2
        implements VersionEntrypoint,
        LegacyFreshSpawnStrategy_1_8_to_1_12.Support,
        LegacyReplacementStrategy_1_8_to_1_12.Support {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 13, 2);
    private static final String SUPPORTED_FAMILY = "1.13-1.13.2";
    private static final VersionCapabilities ENTITY_CAPABILITIES = new VersionCapabilities(
            EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
            false,
            EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
            EntityWorldRegistrationMode.REFERENCE_REWRITE
    );
    private static final VersionBindings ENTITY_BINDINGS = new VersionBindings(
            new EntityFreshSpawnBinding(
                    Arrays.asList(
                            NativeEntityConstructorShape.LEVEL_AND_POSITION,
                            NativeEntityConstructorShape.LEVEL_ONLY
                    ),
                    EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                    true,
                    false
            ),
            new EntityReplacementBinding(
                    EntityWorldRegistrationMode.REFERENCE_REWRITE,
                    true,
                    false
            )
    );
    private static final EntityStrategyBundle ENTITY_STRATEGY_BUNDLE = EntityStrategyBundleSelector.select(
            VERSION,
            ENTITY_CAPABILITIES,
            ENTITY_BINDINGS
    );
    private static final LegacyFreshSpawnStrategy_1_8_to_1_12 FRESH_SPAWN_STRATEGY =
            EntityStrategyBundleSelector.requireLegacyFreshSpawnStrategy(ENTITY_STRATEGY_BUNDLE.freshSpawn());
    private static final LegacyReplacementStrategy_1_8_to_1_12 REPLACEMENT_STRATEGY =
            EntityStrategyBundleSelector.requireLegacyReplacementStrategy(ENTITY_STRATEGY_BUNDLE.replacement());
    private static final GeneratedNativeEntityClassFactory CLASS_FACTORY = new GeneratedNativeEntityClassFactory();
    private static final EntityHookBinderV1_13_2 HOOK_BINDER = new EntityHookBinderV1_13_2();
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUDED_BASE_TYPES = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    private static final Map<CustomEntityBaseType, EntityMetadata> METADATA_REGISTRY = createMetadataRegistry();
    private static final Map<Class<?>, ResolvedEntityTypeMetadata> GENERATED_ENTITY_TYPES =
            new LinkedHashMap<Class<?>, ResolvedEntityTypeMetadata>();

    private final Map<CustomEntityBaseType, ResolvedSpawnMetadata> spawnMetadataRegistry =
            new LinkedHashMap<CustomEntityBaseType, ResolvedSpawnMetadata>();
    private final Map<UUID, ControlledEntity<?>> attachedEntities = new LinkedHashMap<UUID, ControlledEntity<?>>();

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
        return METADATA_REGISTRY.containsKey(baseType);
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
    public <T extends Entity> LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn prepareFreshSpawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        EntityMetadata metadata = requireMetadata(template.baseType());
        return new LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn(
                resolveSpawnMetadata(metadata, spawnOptions.location())
        );
    }

    @Override
    public @NotNull Object createNativeEntity(
            @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
            @NotNull Location location
    ) {
        Objects.requireNonNull(preparedSpawn, "preparedSpawn cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        return createFreshNativeEntity(resolvePreparedSpawnMetadata(preparedSpawn), location);
    }

    @Override
    public <T extends Entity> void bindLifecycleToNativeEntity(
            @NotNull Object nativeEntity,
            @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
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
    public @Nullable Object resolveTrackerEntryHandle(@NotNull Object trackedHandle) {
        Objects.requireNonNull(trackedHandle, "trackedHandle cannot be null");
        return resolveLegacyTrackerEntry(trackedHandle);
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
    public <T extends Entity> LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement prepareReplacement(
            @NotNull T entity,
            @NotNull Object currentNativeHandle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        requireAdvertisedSupportedBaseType(resolveBaseType(entity), "replacement");
        return new LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement(
                new ReplacementMetadata(currentNativeHandle.getClass())
        );
    }

    @Override
    public @NotNull Object allocateReplacementHandle(
            @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
    ) {
        Objects.requireNonNull(preparedReplacement, "preparedReplacement cannot be null");
        return ReflectionSupport.allocateInstance(resolvePreparedReplacementMetadata(preparedReplacement).replacementType());
    }

    @Override
    public <T extends Entity> void bindLifecycleToReplacement(
            @NotNull Object replacementHandle,
            @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement,
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
    public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindBukkitZombieInternal(entity, replacementHandle);
    }

    @Override
    public void rebindLegacyBukkitBridge(
            @NotNull Entity bukkitEntity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(bukkitEntity, "bukkitEntity cannot be null");
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindLegacyBukkitBridgeInternal(bukkitEntity, oldHandle, replacementHandle);
    }

    @Override
    public void replaceWorldReferences(
            @NotNull EntityPublicationFamily family,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (family != EntityPublicationFamily.LEGACY_WORLD_LISTENER) {
            throw unsupportedPublicationFamily(family, "world reference replacement");
        }
        replaceLegacyWorldReferences(oldHandle, replacementHandle);
    }

    @Override
    public void replaceLegacyWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        replaceLegacyWorldReferencesInternal(oldHandle, replacementHandle);
    }

    @Override
    public void rewireLegacyVehicleAndPassengerReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rewireLegacyVehicleAndPassengerReferencesInternal(oldHandle, replacementHandle);
    }

    @Override
    public void refreshLegacyBukkitWrappers(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        refreshLegacyBukkitWrappersInternal(entity);
    }

    @Override
    public void markLegacyEntityRemoved(@NotNull Object oldHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        markLegacyEntityRemovedInternal(oldHandle);
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
        scheduleLegacyReplacementRepairPass(
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
        return "v1_13_2_"
                + metadata.baseType().name()
                + "_"
                + nativeType.getName().replace('.', '_').replace('$', '_')
                + "_FreshSpawnBridge";
    }

    private static @NotNull ResolvedSpawnMetadata resolvePreparedSpawnMetadata(
            @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn
    ) {
        Object preparedMetadata = preparedSpawn.preparedMetadata();
        if (!(preparedMetadata instanceof ResolvedSpawnMetadata)) {
            throw new IllegalStateException(
                    "Prepared legacy fresh-spawn metadata did not contain a ResolvedSpawnMetadata instance."
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
            spawnMetadata = new ResolvedSpawnMetadata(
                    resolveGeneratedTypeMetadata(metadata, probeHandle.getClass())
            );
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
                levelHandle,
                location
        );
        applySpawnLocation(nativeEntity, location);
        return nativeEntity;
    }

    private static @NotNull Object instantiateNativeEntity(
            @NotNull Class<?> generatedType,
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
        Method setPositionRotationMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"setPositionRotation", "setLocation", "moveTo"},
                double.class,
                double.class,
                double.class,
                float.class,
                float.class
        );
        if (setPositionRotationMethod != null) {
            ReflectionSupport.invoke(
                    setPositionRotationMethod,
                    nativeEntity,
                    Double.valueOf(location.getX()),
                    Double.valueOf(location.getY()),
                    Double.valueOf(location.getZ()),
                    Float.valueOf(location.getYaw()),
                    Float.valueOf(location.getPitch())
            );
            return;
        }

        Method setPositionMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"setPosition", "setPos", "a"},
                double.class,
                double.class,
                double.class
        );
        if (setPositionMethod != null) {
            ReflectionSupport.invoke(
                    setPositionMethod,
                    nativeEntity,
                    Double.valueOf(location.getX()),
                    Double.valueOf(location.getY()),
                    Double.valueOf(location.getZ())
            );
        }

        Method setRotationMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"setYawPitch", "setRot", "b"},
                float.class,
                float.class
        );
        if (setRotationMethod != null) {
            ReflectionSupport.invoke(
                    setRotationMethod,
                    nativeEntity,
                    Float.valueOf(location.getYaw()),
                    Float.valueOf(location.getPitch())
            );
            return;
        }

        Field yawField = ReflectionSupport.findField(nativeEntity.getClass(), "yaw", "yRot");
        if (yawField != null) {
            ReflectionSupport.writeField(yawField, nativeEntity, Float.valueOf(location.getYaw()));
        }

        Field pitchField = ReflectionSupport.findField(nativeEntity.getClass(), "pitch", "xRot");
        if (pitchField != null) {
            ReflectionSupport.writeField(pitchField, nativeEntity, Float.valueOf(location.getPitch()));
        }
    }

    private @NotNull EntityMetadata requireMetadata(@NotNull CustomEntityBaseType baseType) {
        requireAdvertisedSupportedBaseType(baseType, "spawn and attach");
        EntityMetadata metadata = METADATA_REGISTRY.get(baseType);
        if (metadata == null) {
            throw new IllegalStateException(
                    "Minecraft " + SUPPORTED_FAMILY + " did not register metadata for supported base type '" + baseType + "'."
            );
        }
        return metadata;
    }

    private static @NotNull Map<CustomEntityBaseType, EntityMetadata> createMetadataRegistry() {
        Map<CustomEntityBaseType, EntityMetadata> metadata = new LinkedHashMap<>();
        for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
            EntityType entityType = baseType.entityTypeOrNull();
            if (entityType == null || entityType.getEntityClass() == null) {
                continue;
            }

            if (PERMANENT_EXCLUDED_BASE_TYPES.contains(baseType)) {
                continue;
            }

            metadata.put(baseType, new EntityMetadata(baseType, entityType));
        }
        return metadata;
//        Map<CustomEntityBaseType, EntityMetadata> metadata = new LinkedHashMap<CustomEntityBaseType, EntityMetadata>();
//        for (CustomEntityBaseType baseType : ADVERTISED_SUPPORTED_BASE_TYPES) {
//            requireExplicitlyAllowedBaseType(baseType);
//            metadata.put(baseType, new EntityMetadata(baseType, requireEntityType(baseType)));
//        }
//        return metadata;
    }

    private static @NotNull Entity spawnVanillaEntity(
            @NotNull Location location,
            @NotNull EntityMetadata metadata
    ) {
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        return world.spawnEntity(location.clone(), metadata.entityType());
    }

    private static @NotNull ReplacementMetadata resolvePreparedReplacementMetadata(
            @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
    ) {
        Object preparedMetadata = preparedReplacement.preparedMetadata();
        if (!(preparedMetadata instanceof ReplacementMetadata)) {
            throw new IllegalStateException(
                    "Prepared legacy replacement metadata did not contain a ReplacementMetadata instance."
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
        if (!METADATA_REGISTRY.containsKey(baseType)) {
            throw new UnsupportedOperationException(
                    "Minecraft " + SUPPORTED_FAMILY + " does not support " + action + " for base type '" + baseType + "'."
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

    private static void rebindLegacyBukkitBridgeInternal(
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

    private static void replaceLegacyWorldReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Object world = resolveEntityWorld(oldHandle);
        if (world == null) {
            return;
        }

        int entityId = resolveEntityId(oldHandle);
        replaceListFieldEntry(world, new String[]{"entityList"}, oldHandle, replacementHandle);
        replaceLookupFieldEntry(world, new String[]{"entitiesById"}, Integer.valueOf(entityId), oldHandle, replacementHandle);
        replaceLookupFieldEntry(
                world,
                new String[]{"entitiesByUUID", "entitiesByUuid"},
                resolveEntityUuid(oldHandle),
                oldHandle,
                replacementHandle
        );
        replaceLegacyChunkSlice(world, oldHandle, replacementHandle);
        updateLegacyTracker(world, entityId, replacementHandle);
    }

    private static @Nullable Object resolveEntityWorld(@NotNull Object entityHandle) {
        Field worldField = ReflectionSupport.findField(entityHandle.getClass(), "world", "level", "m");
        return worldField == null ? null : ReflectionSupport.readField(worldField, entityHandle);
    }

    private static void replaceListFieldEntry(
            @NotNull Object owner,
            @NotNull String[] fieldNames,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        Field field = ReflectionSupport.findField(owner.getClass(), fieldNames);
        if (field == null) {
            return;
        }

        Object values = ReflectionSupport.readField(field, owner);
        if (!(values instanceof List)) {
            return;
        }

        replaceListEntry((List<?>) values, oldValue, newValue);
    }

    private static void replaceLookupFieldEntry(
            @NotNull Object owner,
            @NotNull String[] fieldNames,
            @Nullable Object key,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        if (key == null) {
            return;
        }

        Field field = ReflectionSupport.findField(owner.getClass(), fieldNames);
        if (field == null) {
            return;
        }

        replaceLookupEntry(ReflectionSupport.readField(field, owner), key, oldValue, newValue);
    }

    private static void replaceLookupEntry(
            @Nullable Object lookup,
            @NotNull Object key,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        if (lookup == null) {
            return;
        }

        if (lookup instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> values = (Map<Object, Object>) lookup;
            if (values.get(key) == oldValue) {
                values.put(key, newValue);
            }
            return;
        }

        Object currentValue = lookupByKey(lookup, key);
        if (currentValue != oldValue) {
            return;
        }

        if (key instanceof Integer) {
            Method intSetter = ReflectionSupport.findNamedMethod(lookup.getClass(), new String[]{"a", "put"}, int.class, Object.class);
            if (intSetter != null) {
                ReflectionSupport.invoke(intSetter, lookup, key, newValue);
                return;
            }
        }

        Method objectSetter = ReflectionSupport.findNamedMethod(lookup.getClass(), new String[]{"put"}, Object.class, Object.class);
        if (objectSetter != null) {
            ReflectionSupport.invoke(objectSetter, lookup, key, newValue);
        }
    }

    private static @Nullable Object lookupByKey(@NotNull Object lookup, @NotNull Object key) {
        if (lookup instanceof Map) {
            return ((Map<?, ?>) lookup).get(key);
        }

        if (key instanceof Integer) {
            Method intGetter = ReflectionSupport.findNamedMethod(lookup.getClass(), new String[]{"get"}, int.class);
            if (intGetter != null) {
                return ReflectionSupport.invoke(intGetter, lookup, key);
            }
        }

        Method objectGetter = ReflectionSupport.findNamedMethod(lookup.getClass(), new String[]{"get"}, Object.class);
        if (objectGetter != null) {
            return ReflectionSupport.invoke(objectGetter, lookup, key);
        }
        return null;
    }

    private static void replaceLegacyChunkSlice(
            @NotNull Object world,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (!isEntityMarkedInChunk(oldHandle)) {
            return;
        }

        Object chunk = resolveEntityChunk(world, oldHandle);
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

    private static @Nullable Object resolveEntityChunk(@NotNull Object world, @NotNull Object entityHandle) {
        Method getChunkMethod = ReflectionSupport.findCompatibleMethod(
                world.getClass(),
                new String[]{"getChunkAt"},
                int.class,
                int.class
        );
        if (getChunkMethod == null) {
            return null;
        }

        return ReflectionSupport.invoke(
                getChunkMethod,
                world,
                Integer.valueOf(resolveChunkCoordinate(entityHandle, new String[]{"getChunkX"}, "chunkX", "xChunk", "ae")),
                Integer.valueOf(resolveChunkCoordinate(entityHandle, new String[]{"getChunkZ"}, "chunkZ", "zChunk", "ag"))
        );
    }

    private static @Nullable Collection<Object> resolveChunkSectionEntities(
            @NotNull Object chunk,
            @NotNull Object entityHandle
    ) {
        Field sectionsField = ReflectionSupport.findField(chunk.getClass(), "entitySlices", "entitySections");
        if (sectionsField == null) {
            return null;
        }

        Object sections = ReflectionSupport.readField(sectionsField, chunk);
        if (!(sections instanceof Object[])) {
            return null;
        }

        Object[] values = (Object[]) sections;
        if (values.length == 0) {
            return null;
        }

        int sectionIndex = resolveChunkCoordinate(entityHandle, new String[]{"getChunkY"}, "chunkY", "yChunk", "af");
        if (sectionIndex < 0) {
            sectionIndex = 0;
        } else if (sectionIndex >= values.length) {
            sectionIndex = values.length - 1;
        }

        Object section = values[sectionIndex];
        if (!(section instanceof Collection)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Collection<Object> entities = (Collection<Object>) section;
        return entities;
    }

    private static boolean isEntityMarkedInChunk(@NotNull Object entityHandle) {
        Field inChunkField = ReflectionSupport.findField(entityHandle.getClass(), "inChunk", "ad");
        if (inChunkField == null) {
            return true;
        }

        Object value = ReflectionSupport.readField(inChunkField, entityHandle);
        return !(value instanceof Boolean) || ((Boolean) value).booleanValue();
    }

    private static int resolveChunkCoordinate(
            @NotNull Object entityHandle,
            @NotNull String[] methodNames,
            @NotNull String... fieldNames
    ) {
        Method coordinateMethod = ReflectionSupport.findNamedMethod(entityHandle.getClass(), methodNames);
        if (coordinateMethod != null) {
            Object coordinate = ReflectionSupport.invoke(coordinateMethod, entityHandle);
            if (coordinate instanceof Number) {
                return ((Number) coordinate).intValue();
            }
        }

        Field coordinateField = ReflectionSupport.findField(entityHandle.getClass(), fieldNames);
        if (coordinateField == null) {
            return 0;
        }

        Object coordinate = ReflectionSupport.readField(coordinateField, entityHandle);
        return coordinate instanceof Number ? ((Number) coordinate).intValue() : 0;
    }

    private static void updateLegacyTracker(
            @NotNull Object world,
            int entityId,
            @NotNull Object replacementHandle
    ) {
        Object trackerEntry = resolveLegacyTrackerEntry(world, entityId);
        if (trackerEntry == null) {
            return;
        }

        Field trackerEntityField = ReflectionSupport.findField(trackerEntry.getClass(), "tracker", "trackedEntity", "entity");
        if (trackerEntityField != null) {
            ReflectionSupport.writeField(trackerEntityField, trackerEntry, replacementHandle);
        }
    }

    private static @Nullable Object resolveLegacyTrackerEntry(@NotNull Object trackedHandle) {
        Object world = resolveEntityWorld(trackedHandle);
        if (world == null) {
            return null;
        }
        return resolveLegacyTrackerEntry(world, resolveEntityId(trackedHandle));
    }

    private static @Nullable Object resolveLegacyTrackerEntry(@NotNull Object world, int entityId) {
        Object trackerOwner = resolveTrackerOwner(world);
        if (trackerOwner == null) {
            return null;
        }

        Object trackedEntities = resolveTrackedEntitiesLookup(trackerOwner);
        if (trackedEntities == null) {
            return null;
        }
        return lookupByKey(trackedEntities, Integer.valueOf(entityId));
    }

    private static @Nullable Object resolveTrackerOwner(@NotNull Object world) {
        Field trackerField = ReflectionSupport.findField(world.getClass(), "tracker");
        if (trackerField != null) {
            Object tracker = ReflectionSupport.readField(trackerField, world);
            if (tracker != null) {
                return tracker;
            }
        }

        Method getTrackerMethod = ReflectionSupport.findNamedMethod(world.getClass(), new String[]{"getTracker"});
        if (getTrackerMethod != null) {
            Object tracker = ReflectionSupport.invoke(getTrackerMethod, world);
            if (tracker != null) {
                return tracker;
            }
        }

        Method getPlayerChunkMapMethod = ReflectionSupport.findNamedMethod(world.getClass(), new String[]{"getPlayerChunkMap"});
        if (getPlayerChunkMapMethod != null) {
            Object playerChunkMap = ReflectionSupport.invoke(getPlayerChunkMapMethod, world);
            if (playerChunkMap != null) {
                return playerChunkMap;
            }
        }

        Object chunkProvider = resolveChunkProvider(world);
        if (chunkProvider == null) {
            return null;
        }

        Field playerChunkMapField = ReflectionSupport.findField(
                chunkProvider.getClass(),
                "playerChunkMap",
                "chunkMap",
                "tracker"
        );
        if (playerChunkMapField != null) {
            return ReflectionSupport.readField(playerChunkMapField, chunkProvider);
        }
        return null;
    }

    private static @Nullable Object resolveChunkProvider(@NotNull Object world) {
        Method chunkProviderMethod = ReflectionSupport.findNamedMethod(
                world.getClass(),
                new String[]{"getChunkProvider", "getChunkProviderServer"}
        );
        if (chunkProviderMethod != null) {
            return ReflectionSupport.invoke(chunkProviderMethod, world);
        }

        Field chunkProviderField = ReflectionSupport.findField(world.getClass(), "chunkProvider", "provider");
        return chunkProviderField == null ? null : ReflectionSupport.readField(chunkProviderField, world);
    }

    private static @Nullable Object resolveTrackedEntitiesLookup(@NotNull Object trackerOwner) {
        Field trackedEntitiesField = ReflectionSupport.findField(
                trackerOwner.getClass(),
                "trackedEntities",
                "entityMap"
        );
        if (trackedEntitiesField != null) {
            return ReflectionSupport.readField(trackedEntitiesField, trackerOwner);
        }
        return trackerOwner;
    }

    private static int resolveEntityId(@NotNull Object nativeEntity) {
        Method getIdMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"getId", "getEntityId"});
        if (getIdMethod != null) {
            Object entityId = ReflectionSupport.invoke(getIdMethod, nativeEntity);
            if (entityId instanceof Number) {
                return ((Number) entityId).intValue();
            }
        }

        Field idField = ReflectionSupport.findField(nativeEntity.getClass(), "id", "entityId", "h");
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
        Method uuidMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"getUniqueID", "getUUID"});
        if (uuidMethod != null) {
            return ReflectionSupport.invoke(uuidMethod, nativeEntity);
        }

        Field uuidField = ReflectionSupport.findField(nativeEntity.getClass(), "uniqueID", "uuid", "at");
        return uuidField == null ? null : ReflectionSupport.readField(uuidField, nativeEntity);
    }

    private static void rewireLegacyVehicleAndPassengerReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field vehicleField = ReflectionSupport.findField(oldHandle.getClass(), "vehicle", "ax");
        if (vehicleField == null) {
            return;
        }

        Field passengersField = ReflectionSupport.findField(oldHandle.getClass(), "passengers", "aw");
        if (passengersField != null) {
            Object passengerList = ReflectionSupport.readField(passengersField, oldHandle);
            if (passengerList instanceof List) {
                for (Object passenger : (List<?>) passengerList) {
                    if (passenger == null) {
                        continue;
                    }
                    Field passengerVehicleField = ReflectionSupport.findField(passenger.getClass(), "vehicle", "ax");
                    if (passengerVehicleField != null) {
                        ReflectionSupport.writeField(passengerVehicleField, passenger, replacementHandle);
                    }
                }
            }
        } else {
            Field passengerField = ReflectionSupport.findField(oldHandle.getClass(), "passenger");
            if (passengerField != null) {
                Object passenger = ReflectionSupport.readField(passengerField, oldHandle);
                if (passenger != null) {
                    ReflectionSupport.writeField(vehicleField, passenger, replacementHandle);
                }
            }
        }

        Object vehicle = ReflectionSupport.readField(vehicleField, oldHandle);
        if (vehicle == null) {
            return;
        }

        Field vehiclePassengersField = ReflectionSupport.findField(vehicle.getClass(), "passengers", "aw");
        if (vehiclePassengersField != null) {
            Object vehiclePassengers = ReflectionSupport.readField(vehiclePassengersField, vehicle);
            if (vehiclePassengers instanceof List) {
                List<?> existingPassengers = (List<?>) vehiclePassengers;
                List<Object> replacedPassengers = new ArrayList<Object>(existingPassengers.size());
                for (Object passenger : existingPassengers) {
                    replacedPassengers.add(passenger == oldHandle ? replacementHandle : passenger);
                }
                ReflectionSupport.writeField(vehiclePassengersField, vehicle, replacedPassengers);
            }
            return;
        }

        Field vehiclePassengerField = ReflectionSupport.findField(vehicle.getClass(), "passenger");
        if (vehiclePassengerField != null && ReflectionSupport.readField(vehiclePassengerField, vehicle) == oldHandle) {
            ReflectionSupport.writeField(vehiclePassengerField, vehicle, replacementHandle);
        }
    }

    private static void refreshLegacyBukkitWrappersInternal(@NotNull Entity entity) {
        Field equipmentField = ReflectionSupport.findField(entity.getClass(), "equipment");
        if (equipmentField != null) {
            ReflectionSupport.writeField(equipmentField, entity, null);
        }
    }

    private static void markLegacyEntityRemovedInternal(@NotNull Object oldHandle) {
        Field removedField = ReflectionSupport.findField(oldHandle.getClass(), "dead", "removed");
        if (removedField != null) {
            ReflectionSupport.writeField(removedField, oldHandle, Boolean.TRUE);
        }

        Field validField = ReflectionSupport.findField(oldHandle.getClass(), "valid");
        if (validField != null) {
            ReflectionSupport.writeField(validField, oldHandle, Boolean.FALSE);
        }
    }

    private void scheduleLegacyReplacementRepairPass(
            @NotNull NativeEntityLifecycle<Entity> lifecycle,
            @NotNull Entity entity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        requireRuntimeLifecycle(lifecycle).scheduleNextTickRepair(new Runnable() {
            @Override
            public void run() {
                rebindBukkitZombie(entity, replacementHandle);
                rebindLegacyBukkitBridge(entity, oldHandle, replacementHandle);
                replaceLegacyWorldReferences(oldHandle, replacementHandle);
                rewireLegacyVehicleAndPassengerReferences(oldHandle, replacementHandle);
                refreshLegacyBukkitWrappers(entity);
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

    private static void replaceListEntry(@NotNull List<?> values, @NotNull Object oldValue, @NotNull Object newValue) {
        @SuppressWarnings("unchecked")
        List<Object> mutableValues = (List<Object>) values;
        for (int index = 0; index < mutableValues.size(); index++) {
            if (mutableValues.get(index) == oldValue) {
                mutableValues.set(index, newValue);
            }
        }
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

        private ResolvedSpawnMetadata(@NotNull ResolvedEntityTypeMetadata resolvedMetadata) {
            this.resolvedMetadata = Objects.requireNonNull(resolvedMetadata, "resolvedMetadata cannot be null");
        }

        public @NotNull ResolvedEntityTypeMetadata resolvedMetadata() {
            return resolvedMetadata;
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
}
