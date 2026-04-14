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
package tech.guilhermekaua.spigotboot.entity.v1_21_11;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
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
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.entity.runtime.controller.LogicalEntityHook;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;
import tech.guilhermekaua.spigotboot.entity.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;
import tech.guilhermekaua.spigotboot.entity.runtime.publication.SectionManagerPublicationSupport;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityStrategyBundleSelector;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.EntityVersionEntrypoint;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.EntityStrategyBundle;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;

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
import java.util.stream.Stream;

/**
 * Version-local spawn and attach entrypoint for Minecraft 1.21.11.
 *
 * @since 2.0.2
 */
public final class EntityFactoryV1_21_11
        implements EntityVersionEntrypoint,
        PaperTrackingBindingStrategy_1_21_plus.Support,
        PaperFreshSpawnStrategy_1_21_plus.Support,
        PaperReplacementStrategy_1_21_plus.Support {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 21, 11);
    private static final String HOOK_TICK = "tick";
    private static final String HOOK_MOVE = "move";
    private static final String HOOK_PUSH = "push";
    private static final String HOOK_DAMAGE = "damage";
    private static final String HOOK_INTERACT = "interact";
    private static final String HOOK_DIE = "die";
    private static final String HOOK_REMOVE = "remove";
    private static final String HOOK_REMOVE_WITH_CAUSE = "removeWithCause";
    private static final String HOOK_COLLIDE = "collide";
    private static final String HOOK_POSITION_PASSENGER = "positionPassenger";
    private static final String HOOK_INVENTORY_CHANGE = "inventoryChange";
    private static final String HOOK_INVENTORY_CHANGE_SILENT = "inventoryChangeSilent";
    private static final String HOOK_IS_ALWAYS_TICKED = "isAlwaysTicked";
    private static final Object[] EMPTY_ARGUMENTS = new Object[0];
    private static final EntityVersionCapabilities ENTITY_CAPABILITIES = new EntityVersionCapabilities(
            EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
            true,
            EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
            EntityWorldRegistrationMode.REFERENCE_REWRITE
    );
    private static final EntityVersionBindings ENTITY_BINDINGS = new EntityVersionBindings(
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

    private final GeneratedNativeEntityClassFactory classFactory = new GeneratedNativeEntityClassFactory();
    private final EntityHookBinderV1_21_11 hookBinder = new EntityHookBinderV1_21_11();
    private final Map<CustomEntityBaseType, EntityMetadata> metadataRegistry = createMetadataRegistry();
    private final Map<CustomEntityBaseType, ResolvedSpawnMetadata> spawnMetadataRegistry =
            new LinkedHashMap<CustomEntityBaseType, ResolvedSpawnMetadata>();
    private final Map<Class<?>, ResolvedEntityTypeMetadata> generatedTypes =
            new LinkedHashMap<Class<?>, ResolvedEntityTypeMetadata>();
    /**
     * Returns the runtime capability summary for Minecraft 1.21.11.
     *
     * @return the runtime capability summary
     */
    public static @NotNull EntityVersionCapabilities entityCapabilities() {
        return ENTITY_CAPABILITIES;
    }

    /**
     * Returns the runtime binding bundle for Minecraft 1.21.11.
     *
     * @return the runtime binding bundle
     */
    public static @NotNull EntityVersionBindings entityBindings() {
        return ENTITY_BINDINGS;
    }

    @Override
    public @NotNull EntityVersionCapabilities capabilities() {
        return ENTITY_CAPABILITIES;
    }

    @Override
    public @NotNull EntityVersionBindings bindings() {
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
        return REPLACEMENT_STRATEGY.attach(this, entity, lifecycle);
    }

    private synchronized @NotNull ResolvedEntityTypeMetadata resolveGeneratedTypeMetadata(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        ResolvedEntityTypeMetadata resolvedMetadata = generatedTypes.get(nativeType);
        if (resolvedMetadata != null) {
            return resolvedMetadata;
        }

        Collection<GeneratedNativeHookSpec> hookSpecs = hookBinder.hookSpecs(nativeType);
        if (hookSpecs.isEmpty()) {
            throw new UnsupportedOperationException(
                    "Minecraft 1.21.11 does not expose any supported hooks for base type '" + metadata.baseType() + "'."
            );
        }

        Class<?> generatedType = classFactory.createSubclass(
                nativeType,
                generatedClassName(metadata, nativeType),
                hookSpecs
        );

        resolvedMetadata = new ResolvedEntityTypeMetadata(
                metadata,
                nativeType,
                generatedType,
                hookSpecs,
                hookBinder.supportedHooks(nativeType)
        );
        generatedTypes.put(nativeType, resolvedMetadata);
        return resolvedMetadata;
    }

    @SuppressWarnings("unchecked")
    private void bindRuntimeLifecycle(
            @NotNull Object nativeEntity,
            @NotNull ResolvedEntityTypeMetadata resolvedMetadata,
            @NotNull NativeEntityLifecycle<?> lifecycle
    ) {
        AbstractRuntimeControlledEntity<?> controlledEntity = requireRuntimeLifecycle(lifecycle);
        controlledEntity.bindHookBinder((NativeHookBinder) hookBinder);
        classFactory.installInterceptor(nativeEntity, resolvedMetadata.hookSpecs());
        classFactory.bindLifecycle(nativeEntity, lifecycle);
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

    private static @NotNull Entity spawnVanillaEntity(
            @NotNull Location location,
            @NotNull EntityMetadata metadata
    ) {
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        return world.spawnEntity(location.clone(), metadata.entityType());
    }

    @Override
    public <T extends Entity> @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn prepareFreshSpawn(
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
    public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(world.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, world);
    }

    @Override
    public @NotNull PaperFreshSpawnStrategy_1_21_plus.TrackingHandles resolveTrackingHandles(
            @NotNull Object nativeEntity
    ) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        TrackedEntityState trackedEntityState = resolveTrackedEntityState(nativeEntity);
        return new PaperFreshSpawnStrategy_1_21_plus.TrackingHandles(
                trackedEntityState.trackedEntity(),
                trackedEntityState.serverEntity()
        );
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
    public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        return resolveNativeHandle(entity);
    }

    @Override
    public <T extends Entity> @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement prepareReplacement(
            @NotNull T entity,
            @NotNull Object currentNativeHandle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        EntityMetadata metadata = requireMetadata(resolveBaseType(entity));
        return new PaperReplacementStrategy_1_21_plus.PreparedReplacement(
                resolveGeneratedTypeMetadata(metadata, currentNativeHandle.getClass())
        );
    }

    @Override
    public @NotNull Object allocateReplacementHandle(
            @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
    ) {
        Objects.requireNonNull(preparedReplacement, "preparedReplacement cannot be null");
        return ReflectionSupport.allocateInstance(resolvePreparedReplacementMetadata(preparedReplacement).generatedType());
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
        bindRuntimeLifecycle(replacementHandle, resolvePreparedReplacementMetadata(preparedReplacement), lifecycle);
    }

    @Override
    public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindBukkitZombieInternal(entity, replacementHandle);
    }

    @Override
    public void rebindModernBukkitBridge(
            @NotNull Entity entity,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindModernBukkitBridgeInternal(entity, currentNativeHandle, replacementHandle);
    }

    @Override
    public void replaceWorldReferences(
            @NotNull EntityPublicationFamily family,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(family, "family cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        switch (family) {
            case SECTION_MANAGER:
                replaceSectionManagerWorldReferencesInternal(currentNativeHandle, replacementHandle);
                return;
            case PAPER_MOONRISE_CHUNK_SYSTEM:
                replaceModernWorldReferencesInternal(currentNativeHandle, replacementHandle);
                return;
            default:
                throw new IllegalStateException(
                        "Minecraft 1.21.11 does not support publication family '" + family.id() + "'."
                );
        }
    }

    @Override
    public void replaceModernWorldReferences(@NotNull Object currentNativeHandle, @NotNull Object replacementHandle) {
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        replaceModernWorldReferencesInternal(currentNativeHandle, replacementHandle);
    }

    @Override
    public void rewireModernVehicleAndPassengerReferences(
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rewireModernVehicleAndPassengerReferencesInternal(currentNativeHandle, replacementHandle);
    }

    @Override
    public void refreshModernBukkitWrappers(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        refreshModernBukkitWrappersInternal(entity);
    }

    @Override
    public void markModernEntityRemoved(@NotNull Object currentNativeHandle) {
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        markModernEntityRemovedInternal(currentNativeHandle);
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

    private static @NotNull ResolvedEntityTypeMetadata resolvePreparedReplacementMetadata(
            @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
    ) {
        Object preparedMetadata = preparedReplacement.preparedMetadata();
        if (!(preparedMetadata instanceof ResolvedEntityTypeMetadata)) {
            throw new IllegalStateException(
                    "Prepared paper replacement metadata did not contain a ResolvedEntityTypeMetadata instance."
            );
        }
        return (ResolvedEntityTypeMetadata) preparedMetadata;
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
            Object nativeEntityType = ReflectionSupport.invoke(
                    ReflectionSupport.requireNamedMethod(probeHandle.getClass(), new String[]{"getType"}),
                    probeHandle
            );
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
        Class<?> generatedType = spawnMetadata.resolvedMetadata().generatedType();
        Object nativeEntity = instantiateNativeEntity(
                generatedType,
                spawnMetadata.nativeEntityType(),
                levelHandle,
                location
        );
        applySpawnLocation(nativeEntity, location);
        return nativeEntity;
    }

    private static @NotNull Object instantiateNativeEntity(
            @NotNull Class<?> generatedType,
            @NotNull Object nativeEntityType,
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

        Constructor<?> typedConstructor = findConstructor(generatedType, nativeEntityType.getClass(), levelHandle.getClass());
        if (typedConstructor != null) {
            return ReflectionSupport.instantiate(typedConstructor, nativeEntityType, levelHandle);
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

    private static @NotNull TrackedEntityState resolveTrackedEntityState(@NotNull Object nativeEntity) {
        Method trackedEntityMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"moonrise$getTrackedEntity"}
        );
        if (trackedEntityMethod == null) {
            return TrackedEntityState.untracked();
        }

        Object trackedEntity = ReflectionSupport.invoke(trackedEntityMethod, nativeEntity);
        if (trackedEntity == null) {
            return TrackedEntityState.untracked();
        }

        Field serverEntityField = ReflectionSupport.findField(trackedEntity.getClass(), "serverEntity");
        Object serverEntity = serverEntityField == null ? null : ReflectionSupport.readField(serverEntityField, trackedEntity);
        return new TrackedEntityState(trackedEntity, serverEntity);
    }

    private @NotNull EntityMetadata requireMetadata(@NotNull CustomEntityBaseType baseType) {
        EntityMetadata metadata = metadataRegistry.get(baseType);
        if (metadata == null) {
            throw new UnsupportedOperationException(
                    "Minecraft 1.21.11 does not support spawn and attach for base type '" + baseType + "'."
            );
        }
        return metadata;
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

    private static @NotNull Map<CustomEntityBaseType, EntityMetadata> createMetadataRegistry() {
        Map<CustomEntityBaseType, EntityMetadata> metadata = new LinkedHashMap<CustomEntityBaseType, EntityMetadata>();
        for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
            EntityType entityType = baseType.entityTypeOrNull();
            if (entityType == null || entityType.getEntityClass() == null) {
                continue;
            }
            if (baseType == CustomEntityBaseType.UNKNOWN
                    || baseType == CustomEntityBaseType.PLAYER
                    || baseType == CustomEntityBaseType.WEATHER
                    || baseType == CustomEntityBaseType.COMPLEX_PART) {
                continue;
            }
            metadata.put(baseType, new EntityMetadata(baseType, entityType));
        }
        return metadata;
    }

    private static @NotNull String generatedClassName(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        return "tech.guilhermekaua.spigotboot.entity.generated.v1_21_11.SpigotBoot"
                + toGeneratedSuffix(metadata.baseType())
                + "V1_21_11_"
                + Integer.toHexString(nativeType.getName().hashCode()).replace('-', '0');
    }

    private static @NotNull String toGeneratedSuffix(@NotNull CustomEntityBaseType baseType) {
        StringBuilder suffix = new StringBuilder();
        for (String token : baseType.name().toLowerCase().split("_")) {
            if (token.isEmpty()) {
                continue;
            }
            suffix.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                suffix.append(token.substring(1));
            }
        }
        return suffix.toString();
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
        Field bukkitEntityField = ReflectionSupport.findField(oldHandle.getClass(), "bukkitEntity");
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
        Object level = ReflectionSupport.readField(ReflectionSupport.requireField(oldHandle.getClass(), "level"), oldHandle);
        Object entityLookup = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(level.getClass(), new String[]{"moonrise$getEntityLookup"}),
                level
        );
        int entityId = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getId"}),
                oldHandle
        )).intValue();
        Object uuid = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getUUID"}),
                oldHandle
        );

        Object entityById = ReflectionSupport.readField(ReflectionSupport.requireField(entityLookup.getClass(), "entityById"), entityLookup);
        ReflectionSupport.invoke(
                ReflectionSupport.requireCompatibleMethod(entityById.getClass(), new String[]{"put"}, long.class, replacementHandle.getClass()),
                entityById,
                Long.valueOf(entityId),
                replacementHandle
        );

        @SuppressWarnings("unchecked")
        Map<Object, Object> entityByUuid = (Map<Object, Object>) ReflectionSupport.readField(
                ReflectionSupport.requireField(entityLookup.getClass(), "entityByUUID"),
                entityLookup
        );
        entityByUuid.put(uuid, replacementHandle);

        Object accessibleEntities = ReflectionSupport.readField(
                ReflectionSupport.requireField(entityLookup.getClass(), "accessibleEntities"),
                entityLookup
        );
        ReflectionSupport.invoke(
                ReflectionSupport.requireCompatibleMethod(accessibleEntities.getClass(), new String[]{"remove"}, oldHandle.getClass()),
                accessibleEntities,
                oldHandle
        );
        ReflectionSupport.invoke(
                ReflectionSupport.requireCompatibleMethod(accessibleEntities.getClass(), new String[]{"add"}, replacementHandle.getClass()),
                accessibleEntities,
                replacementHandle
        );

        int sectionX = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"moonrise$getSectionX"}),
                oldHandle
        )).intValue();
        int sectionY = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"moonrise$getSectionY"}),
                oldHandle
        )).intValue();
        int sectionZ = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"moonrise$getSectionZ"}),
                oldHandle
        )).intValue();

        Object chunkSlices = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(entityLookup.getClass(), new String[]{"getChunk"}, int.class, int.class),
                entityLookup,
                Integer.valueOf(sectionX),
                Integer.valueOf(sectionZ)
        );
        if (chunkSlices == null) {
            chunkSlices = ReflectionSupport.invoke(
                    ReflectionSupport.requireNamedMethod(entityLookup.getClass(), new String[]{"getOrCreateChunk"}, int.class, int.class),
                    entityLookup,
                    Integer.valueOf(sectionX),
                    Integer.valueOf(sectionZ)
            );
        }
        if (chunkSlices != null) {
            ReflectionSupport.invoke(
                    ReflectionSupport.requireCompatibleMethod(chunkSlices.getClass(), new String[]{"removeEntity"}, oldHandle.getClass(), int.class),
                    chunkSlices,
                    oldHandle,
                    Integer.valueOf(sectionY)
            );
            ReflectionSupport.invoke(
                    ReflectionSupport.requireCompatibleMethod(chunkSlices.getClass(), new String[]{"addEntity"}, replacementHandle.getClass(), int.class),
                    chunkSlices,
                    replacementHandle,
                    Integer.valueOf(sectionY)
            );
        }

        if (ReflectionSupport.findField(entityLookup.getClass(), "trackerEntities") != null) {
            Object trackerEntities = ReflectionSupport.readField(
                    ReflectionSupport.requireField(entityLookup.getClass(), "trackerEntities"),
                    entityLookup
            );
            ReflectionSupport.invoke(
                    ReflectionSupport.requireCompatibleMethod(trackerEntities.getClass(), new String[]{"remove"}, oldHandle.getClass()),
                    trackerEntities,
                    oldHandle
            );
            ReflectionSupport.invoke(
                    ReflectionSupport.requireCompatibleMethod(trackerEntities.getClass(), new String[]{"add"}, replacementHandle.getClass()),
                    trackerEntities,
                    replacementHandle
            );
        }

        updateTrackedEntity(level, oldHandle, replacementHandle, entityId);
        rebindModernLevelCallback(oldHandle, replacementHandle);
        replaceModernLifecycleCollections(level, oldHandle, replacementHandle);
    }

    private static void replaceSectionManagerWorldReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Object level = ReflectionSupport.readField(ReflectionSupport.requireField(oldHandle.getClass(), "level"), oldHandle);
        replaceSectionManagerVisibleStorage(level, oldHandle, replacementHandle);
        rebindModernLevelCallback(oldHandle, replacementHandle);
        replaceModernLifecycleCollections(level, oldHandle, replacementHandle);
    }

    private static void replaceSectionManagerVisibleStorage(
            @NotNull Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field entityManagerField = ReflectionSupport.findField(level.getClass(), "entityManager");
        if (entityManagerField == null) {
            return;
        }

        Object entityManager = ReflectionSupport.readField(entityManagerField, level);
        if (entityManager == null) {
            return;
        }

        Field visibleEntityStorageField = ReflectionSupport.findField(entityManager.getClass(), "visibleEntityStorage");
        if (visibleEntityStorageField == null) {
            return;
        }

        Object visibleEntityStorage = ReflectionSupport.readField(visibleEntityStorageField, entityManager);
        if (visibleEntityStorage == null) {
            return;
        }

        int entityId = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getId"}),
                oldHandle
        )).intValue();
        Object uuid = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getUUID"}),
                oldHandle
        );

        replaceMapEntryByKey(visibleEntityStorage, "byId", Integer.valueOf(entityId), oldHandle, replacementHandle);
        replaceMapEntryByKey(visibleEntityStorage, "byUUID", uuid, oldHandle, replacementHandle);
        replaceMapEntryByKey(visibleEntityStorage, "byUuid", uuid, oldHandle, replacementHandle);
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

    private static void updateTrackedEntity(
            @NotNull Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle,
            int entityId
    ) {
        Object trackedEntity = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"moonrise$getTrackedEntity"}),
                oldHandle
        );
        if (trackedEntity == null) {
            return;
        }

        Object chunkSource = ReflectionSupport.readField(ReflectionSupport.requireField(level.getClass(), "chunkSource"), level);
        Object chunkMap = ReflectionSupport.readField(ReflectionSupport.requireField(chunkSource.getClass(), "chunkMap"), chunkSource);
        Object entityMap = ReflectionSupport.readField(ReflectionSupport.requireField(chunkMap.getClass(), "entityMap"), chunkMap);
        ReflectionSupport.invoke(
                ReflectionSupport.requireCompatibleMethod(entityMap.getClass(), new String[]{"put"}, int.class, trackedEntity.getClass()),
                entityMap,
                Integer.valueOf(entityId),
                trackedEntity
        );

        ReflectionSupport.writeField(ReflectionSupport.requireField(trackedEntity.getClass(), "entity"), trackedEntity, replacementHandle);
        Object serverEntity = ReflectionSupport.readField(
                ReflectionSupport.requireField(trackedEntity.getClass(), "serverEntity"),
                trackedEntity
        );
        ReflectionSupport.writeField(ReflectionSupport.requireField(serverEntity.getClass(), "entity"), serverEntity, replacementHandle);

        Method setTrackedEntityMethod = ReflectionSupport.requireCompatibleMethod(
                replacementHandle.getClass(),
                new String[]{"moonrise$setTrackedEntity"},
                trackedEntity.getClass()
        );
        ReflectionSupport.invoke(setTrackedEntityMethod, replacementHandle, trackedEntity);
        ReflectionSupport.invoke(setTrackedEntityMethod, oldHandle, (Object) null);
    }

    private static void rebindModernLevelCallback(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Field levelCallbackField = ReflectionSupport.findField(oldHandle.getClass(), "levelCallback");
        if (levelCallbackField == null) {
            return;
        }

        Object oldLevelCallback = ReflectionSupport.readField(levelCallbackField, oldHandle);
        if (oldLevelCallback == null) {
            return;
        }

        migrateModernSectionMembership(oldLevelCallback, oldHandle, replacementHandle);

        Object replacementLevelCallback = retargetModernSectionCallback(
                oldLevelCallback,
                oldHandle,
                replacementHandle
        );
        if (replacementLevelCallback == null) {
            replacementLevelCallback = recreateModernSectionCallback(oldLevelCallback, replacementHandle);
        }
        if (replacementLevelCallback == null) {
            return;
        }

        Method setReplacementLevelCallbackMethod = ReflectionSupport.requireCompatibleMethod(
                replacementHandle.getClass(),
                new String[]{"setLevelCallback"},
                replacementLevelCallback.getClass()
        );
        ReflectionSupport.invoke(setReplacementLevelCallbackMethod, replacementHandle, replacementLevelCallback);

        Class<?> entityInLevelCallbackType = ReflectionSupport.requireClass(
                "net.minecraft.world.level.entity.EntityInLevelCallback"
        );
        Object nullLevelCallback = ReflectionSupport.readField(
                ReflectionSupport.requireField(entityInLevelCallbackType, "NULL"),
                null
        );
        Method clearOldLevelCallbackMethod = ReflectionSupport.requireCompatibleMethod(
                oldHandle.getClass(),
                new String[]{"setLevelCallback"},
                nullLevelCallback.getClass()
        );
        ReflectionSupport.invoke(clearOldLevelCallbackMethod, oldHandle, nullLevelCallback);
    }

    static @Nullable Object retargetModernSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        return SectionManagerPublicationSupport.retargetSectionCallback(oldLevelCallback, oldHandle, replacementHandle);
    }

    static @Nullable Object recreateModernSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object replacementHandle
    ) {
        return SectionManagerPublicationSupport.recreateSectionCallback(oldLevelCallback, replacementHandle);
    }

    private static @Nullable Constructor<?> findModernSectionCallbackConstructor(
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

    static void migrateModernSectionMembership(
            @Nullable Object oldLevelCallback,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        SectionManagerPublicationSupport.migrateSectionMembership(oldLevelCallback, oldHandle, replacementHandle);
    }

    private static void replaceModernLifecycleCollections(
            @NotNull Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        replaceManagedCollectionField(level, "entityTickList", oldHandle, replacementHandle);
        replaceManagedCollectionField(level, "navigatingMobs", oldHandle, replacementHandle);
    }

    private static void replaceManagedCollectionField(
            @NotNull Object owner,
            @NotNull String fieldName,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        Field field = ReflectionSupport.findField(owner.getClass(), fieldName);
        if (field == null) {
            return;
        }

        Object collection = ReflectionSupport.readField(field, owner);
        replaceManagedCollectionEntry(collection, oldValue, newValue);
    }

    static void replaceManagedCollectionEntry(
            @Nullable Object collection,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        SectionManagerPublicationSupport.replaceManagedCollectionEntry(collection, oldValue, newValue);
    }

    private static boolean containsManagedEntry(@Nullable Object collection, @NotNull Object value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (collection == null) {
            return false;
        }

        Method containsMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"contains"},
                value.getClass()
        );
        if (containsMethod != null) {
            Object result = ReflectionSupport.invoke(containsMethod, collection, value);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        }

        if (collection instanceof Collection) {
            return ((Collection<?>) collection).contains(value);
        }

        Method getEntitiesMethod = ReflectionSupport.findNamedMethod(collection.getClass(), new String[]{"getEntities"});
        if (getEntitiesMethod == null) {
            return false;
        }

        Object entities = ReflectionSupport.invoke(getEntitiesMethod, collection);
        if (entities instanceof Stream) {
            try (Stream<?> stream = (Stream<?>) entities) {
                return stream.anyMatch(candidate -> candidate == value);
            }
        }
        if (entities instanceof Iterable) {
            for (Object candidate : (Iterable<?>) entities) {
                if (candidate == value) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void rewireModernVehicleAndPassengerReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field passengersField = ReflectionSupport.requireField(oldHandle.getClass(), "passengers");
        Field vehicleField = ReflectionSupport.requireField(oldHandle.getClass(), "vehicle");

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
        Object discarded = ReflectionSupport.readField(ReflectionSupport.requireField(removalReasonType, "DISCARDED"), null);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "removalReason"), oldHandle, discarded);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "valid"), oldHandle, Boolean.FALSE);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "inWorld"), oldHandle, Boolean.FALSE);
        if (ReflectionSupport.findField(oldHandle.getClass(), "pluginRemoved") != null) {
            ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "pluginRemoved"), oldHandle, Boolean.TRUE);
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

    private static final class ResolvedEntityTypeMetadata {
        private final EntityMetadata metadata;
        private final Class<?> nativeType;
        private final Class<?> generatedType;
        private final Collection<GeneratedNativeHookSpec> hookSpecs;
        private final EnumSet<LogicalEntityHook> supportedHooks;

        private ResolvedEntityTypeMetadata(
                @NotNull EntityMetadata metadata,
                @NotNull Class<?> nativeType,
                @NotNull Class<?> generatedType,
                @NotNull Collection<GeneratedNativeHookSpec> hookSpecs,
                @NotNull EnumSet<LogicalEntityHook> supportedHooks
        ) {
            this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
            this.nativeType = Objects.requireNonNull(nativeType, "nativeType cannot be null");
            this.generatedType = Objects.requireNonNull(generatedType, "generatedType cannot be null");
            this.hookSpecs = Objects.requireNonNull(hookSpecs, "hookSpecs cannot be null");
            this.supportedHooks = Objects.requireNonNull(supportedHooks, "supportedHooks cannot be null");
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

        public @NotNull EnumSet<LogicalEntityHook> supportedHooks() {
            return supportedHooks.clone();
        }
    }

    private static final class ResolvedSpawnMetadata {
        private final ResolvedEntityTypeMetadata resolvedMetadata;
        private final Object nativeEntityType;

        private ResolvedSpawnMetadata(
                @NotNull ResolvedEntityTypeMetadata resolvedMetadata,
                @NotNull Object nativeEntityType
        ) {
            this.resolvedMetadata = Objects.requireNonNull(resolvedMetadata, "resolvedMetadata cannot be null");
            this.nativeEntityType = Objects.requireNonNull(nativeEntityType, "nativeEntityType cannot be null");
        }

        public @NotNull ResolvedEntityTypeMetadata resolvedMetadata() {
            return resolvedMetadata;
        }

        public @NotNull Object nativeEntityType() {
            return nativeEntityType;
        }
    }

    private final class EntityHookBinderV1_21_11 implements NativeHookBinder<Entity> {
        private final Class<?> entityType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity");
        private final Class<?> moverType = ReflectionSupport.requireClass("net.minecraft.world.entity.MoverType");
        private final Class<?> vec3Type = ReflectionSupport.requireClass("net.minecraft.world.phys.Vec3");
        private final Class<?> serverLevelType = ReflectionSupport.requireClass("net.minecraft.server.level.ServerLevel");
        private final Class<?> damageSourceType = ReflectionSupport.requireClass("net.minecraft.world.damagesource.DamageSource");
        private final Class<?> playerType = ReflectionSupport.requireClass("net.minecraft.world.entity.player.Player");
        private final Class<?> interactionHandType = ReflectionSupport.requireClass("net.minecraft.world.InteractionHand");
        private final Class<?> interactionResultType = ReflectionSupport.requireClass("net.minecraft.world.InteractionResult");
        private final Class<?> interactionResultSuccessType =
                ReflectionSupport.requireClass("net.minecraft.world.InteractionResult$Success");
        private final Class<?> removalReasonType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity$RemovalReason");
        private final Class<?> moveFunctionType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity$MoveFunction");
        private final Class<?> equipmentSlotType = ReflectionSupport.requireClass("net.minecraft.world.entity.EquipmentSlot");
        private final Class<?> itemStackType = ReflectionSupport.requireClass("net.minecraft.world.item.ItemStack");
        private final Field interactionResultSuccessField = ReflectionSupport.requireField(interactionResultType, "SUCCESS");
        private final Field interactionResultConsumeField = ReflectionSupport.requireField(interactionResultType, "CONSUME");
        private final Field interactionResultFailField = ReflectionSupport.requireField(interactionResultType, "FAIL");
        private final Field interactionResultPassField = ReflectionSupport.requireField(interactionResultType, "PASS");
        private final Field interactionResultTryWithEmptyHandField =
                ReflectionSupport.requireField(interactionResultType, "TRY_WITH_EMPTY_HAND");
        private final Method interactionResultSuccessSwingSourceMethod =
                ReflectionSupport.requireNamedMethod(interactionResultSuccessType, new String[]{"swingSource"});

        public @NotNull EnumSet<LogicalEntityHook> supportedHooks(@NotNull Class<?> nativeType) {
            return resolveHookCatalog(nativeType).supportedHooks();
        }

        @Override
        public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs(@NotNull Class<?> nativeType) {
            return resolveHookCatalog(nativeType).hookSpecs();
        }

        @Override
        public @Nullable Object dispatch(
                @NotNull AbstractRuntimeControlledEntity<Entity> controlledEntity,
                @NotNull LifecycleAwareNativeEntity nativeEntity,
                @NotNull String hookName,
                @Nullable Object[] arguments
        ) {
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
                Object[] rawArguments = requireArguments(arguments, 2);
                controlledEntity.dispatchMove(
                        resolveVecX(rawArguments[1]),
                        resolveVecY(rawArguments[1]),
                        resolveVecZ(rawArguments[1]),
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
                Object[] rawArguments = requireArguments(arguments, 3);
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
                Object[] rawArguments = requireArguments(arguments, 3);
                return Boolean.valueOf(controlledEntity.dispatchDamage(
                        ((Float) rawArguments[2]).floatValue(),
                        new ContextualBaseInvoker<EntityDamageContext<Entity>, Boolean>() {
                            @Override
                            public Boolean invoke(@NotNull EntityDamageContext<Entity> context) {
                                Object result = nativeEntity.spigotBootInvokeBase(
                                        HOOK_DAMAGE,
                                        new Object[]{rawArguments[0], rawArguments[1], Float.valueOf(context.amount())}
                                );
                                return Boolean.valueOf(result != null && ((Boolean) result).booleanValue());
                            }
                        }
                ));
            }

            if (HOOK_INTERACT.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 2);
                Player player = (Player) resolveBukkitEntity(rawArguments[0]);
                Object[] baseInteractionResultHolder = new Object[1];
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
                Object[] rawArguments = requireArguments(arguments, 1);
                controlledEntity.dispatchDie(new ContextualBaseInvoker<EntityDieContext<Entity>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityDieContext<Entity> context) {
                        nativeEntity.spigotBootInvokeBase(HOOK_DIE, new Object[]{rawArguments[0]});
                        return null;
                    }
                });
                return null;
            }

            if (HOOK_REMOVE.equals(hookName) || HOOK_REMOVE_WITH_CAUSE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, HOOK_REMOVE.equals(hookName) ? 1 : 2);
                controlledEntity.dispatchRemove(new ContextualBaseInvoker<EntityRemoveContext<Entity>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityRemoveContext<Entity> context) {
                        nativeEntity.spigotBootInvokeBase(hookName, rawArguments);
                        return null;
                    }
                });
                return null;
            }

            if (HOOK_COLLIDE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 1);
                controlledEntity.dispatchCollide(
                        resolveBukkitEntity(rawArguments[0]),
                        new ContextualBaseInvoker<EntityCollideContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityCollideContext<Entity> context) {
                                nativeEntity.spigotBootInvokeBase(HOOK_COLLIDE, new Object[]{rawArguments[0]});
                                return null;
                            }
                        }
                );
                return null;
            }

            if (HOOK_POSITION_PASSENGER.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 2);
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

            if (HOOK_INVENTORY_CHANGE.equals(hookName) || HOOK_INVENTORY_CHANGE_SILENT.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, HOOK_INVENTORY_CHANGE.equals(hookName) ? 3 : 4);
                controlledEntity.dispatchInventoryChange(
                        toApiEquipmentSlot(rawArguments[0]),
                        toBukkitItem(rawArguments[1]),
                        toBukkitItem(rawArguments[2]),
                        new ContextualBaseInvoker<EntityInventoryChangeContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityInventoryChangeContext<Entity> context) {
                                if (HOOK_INVENTORY_CHANGE_SILENT.equals(hookName)) {
                                    nativeEntity.spigotBootInvokeBase(
                                            HOOK_INVENTORY_CHANGE_SILENT,
                                            new Object[]{
                                                    toNmsEquipmentSlot(context.slot()),
                                                    toNmsItem(context.previousItem()),
                                                    toNmsItem(context.newItem()),
                                                    rawArguments[3]
                                            }
                                    );
                                } else {
                                    nativeEntity.spigotBootInvokeBase(
                                            HOOK_INVENTORY_CHANGE,
                                            new Object[]{
                                                    toNmsEquipmentSlot(context.slot()),
                                                    toNmsItem(context.previousItem()),
                                                    toNmsItem(context.newItem())
                                            }
                                    );
                                }
                                return null;
                            }
                        }
                );
                return null;
            }

            if (HOOK_IS_ALWAYS_TICKED.equals(hookName)) {
                return Boolean.TRUE;
            }

            throw new IllegalArgumentException("Unknown 1.21.11 Entity hook: " + hookName);
        }

        private @Nullable Method requireMethodByNameAndCount(
                @NotNull Class<?> type,
                @NotNull String methodName,
                int parameterCount
        ) {
            Class<?> current = type;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (methodName.equals(method.getName()) && method.getParameterTypes().length == parameterCount) {
                        method.setAccessible(true);
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private @NotNull ResolvedHookCatalog resolveHookCatalog(@NotNull Class<?> nativeType) {
            List<GeneratedNativeHookSpec> hookSpecs = new ArrayList<GeneratedNativeHookSpec>();
            EnumSet<LogicalEntityHook> supportedHooks = EnumSet.noneOf(LogicalEntityHook.class);

            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.TICK,
                    HOOK_TICK,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"tick"})
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.MOVE,
                    HOOK_MOVE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"move"}, moverType, vec3Type)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.PUSH,
                    HOOK_PUSH,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"push"}, double.class, double.class, double.class)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.DAMAGE,
                    HOOK_DAMAGE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"hurtServer"}, serverLevelType, damageSourceType, float.class)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.INTERACT,
                    HOOK_INTERACT,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"interact"}, playerType, interactionHandType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.DIE,
                    HOOK_DIE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"die"}, damageSourceType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.REMOVE,
                    HOOK_REMOVE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"remove"}, removalReasonType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.REMOVE,
                    HOOK_REMOVE_WITH_CAUSE,
                    requireMethodByNameAndCount(nativeType, "remove", 2)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.COLLIDE,
                    HOOK_COLLIDE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"push"}, entityType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.POSITION_PASSENGER,
                    HOOK_POSITION_PASSENGER,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"positionRider"}, entityType, moveFunctionType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.INVENTORY_CHANGE,
                    HOOK_INVENTORY_CHANGE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"onEquipItem"}, equipmentSlotType, itemStackType, itemStackType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.INVENTORY_CHANGE,
                    HOOK_INVENTORY_CHANGE_SILENT,
                    requireMethodByNameAndCount(nativeType, "onEquipItem", 4)
            );
            addNativeHookSpec(
                    hookSpecs,
                    HOOK_IS_ALWAYS_TICKED,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{HOOK_IS_ALWAYS_TICKED})
            );

            return new ResolvedHookCatalog(hookSpecs, supportedHooks);
        }

        private void addHookSpec(
                @NotNull List<GeneratedNativeHookSpec> hookSpecs,
                @NotNull EnumSet<LogicalEntityHook> supportedHooks,
                @NotNull LogicalEntityHook logicalHook,
                @NotNull String hookName,
                @Nullable Method method
        ) {
            if (method == null) {
                return;
            }
            hookSpecs.add(GeneratedNativeHookSpec.of(hookName, method));
            supportedHooks.add(logicalHook);
        }

        private void addNativeHookSpec(
                @NotNull List<GeneratedNativeHookSpec> hookSpecs,
                @NotNull String hookName,
                @Nullable Method method
        ) {
            if (method == null) {
                return;
            }
            hookSpecs.add(GeneratedNativeHookSpec.of(hookName, method));
        }

        private final class ResolvedHookCatalog {
            private final Collection<GeneratedNativeHookSpec> hookSpecs;
            private final EnumSet<LogicalEntityHook> supportedHooks;

            private ResolvedHookCatalog(
                    @NotNull Collection<GeneratedNativeHookSpec> hookSpecs,
                    @NotNull EnumSet<LogicalEntityHook> supportedHooks
            ) {
                this.hookSpecs = Objects.requireNonNull(hookSpecs, "hookSpecs cannot be null");
                this.supportedHooks = Objects.requireNonNull(supportedHooks, "supportedHooks cannot be null");
            }

            public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs() {
                return hookSpecs;
            }

            public @NotNull EnumSet<LogicalEntityHook> supportedHooks() {
                return supportedHooks.clone();
            }
        }

        private @NotNull Object[] requireArguments(@Nullable Object[] arguments, int expectedLength) {
            if (arguments == null || arguments.length != expectedLength) {
                throw new IllegalArgumentException(
                        "Expected " + expectedLength + " native hook arguments but received "
                                + (arguments == null ? 0 : arguments.length) + "."
                );
            }
            return arguments;
        }

        private double resolveVecX(@NotNull Object vec3) {
            return ((Double) ReflectionSupport.readField(ReflectionSupport.requireField(vec3Type, "x"), vec3)).doubleValue();
        }

        private double resolveVecY(@NotNull Object vec3) {
            return ((Double) ReflectionSupport.readField(ReflectionSupport.requireField(vec3Type, "y"), vec3)).doubleValue();
        }

        private double resolveVecZ(@NotNull Object vec3) {
            return ((Double) ReflectionSupport.readField(ReflectionSupport.requireField(vec3Type, "z"), vec3)).doubleValue();
        }

        private @NotNull Object createVec3(double x, double y, double z) {
            Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(vec3Type, double.class, double.class, double.class);
            return ReflectionSupport.instantiate(constructor, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
        }

        private @NotNull EntityInteractionHand toApiHand(@NotNull Object hand) {
            return "OFF_HAND".equals(((Enum<?>) hand).name())
                    ? EntityInteractionHand.OFF_HAND
                    : EntityInteractionHand.MAIN_HAND;
        }

        private @NotNull EntityInteractionResult toApiInteractionResult(@Nullable Object interactionResult) {
            if (interactionResult == null) {
                return EntityInteractionResult.PASS;
            }
            if (interactionResult.equals(readInteractionResult(interactionResultFailField))) {
                return EntityInteractionResult.FAIL;
            }
            if (interactionResult.equals(readInteractionResult(interactionResultPassField))
                    || interactionResult.equals(readInteractionResult(interactionResultTryWithEmptyHandField))) {
                return EntityInteractionResult.PASS;
            }
            if (interactionResultSuccessType.isInstance(interactionResult)) {
                if (interactionResult.equals(readInteractionResult(interactionResultConsumeField))) {
                    return EntityInteractionResult.CONSUME;
                }
                Object swingSource = ReflectionSupport.invoke(
                        interactionResultSuccessSwingSourceMethod,
                        interactionResult
                );
                if (swingSource instanceof Enum<?> && "NONE".equals(((Enum<?>) swingSource).name())) {
                    return EntityInteractionResult.CONSUME;
                }
                return EntityInteractionResult.SUCCESS;
            }
            return EntityInteractionResult.PASS;
        }

        private @NotNull Object toNmsInteractionResult(
                @NotNull EntityInteractionResult result,
                @Nullable Object baseInteractionResult
        ) {
            if (baseInteractionResult != null
                    && interactionResultType.isInstance(baseInteractionResult)
                    && result == toApiInteractionResult(baseInteractionResult)) {
                return baseInteractionResult;
            }
            if (result == EntityInteractionResult.SUCCESS) {
                return readInteractionResult(interactionResultSuccessField);
            }
            if (result == EntityInteractionResult.CONSUME) {
                return readInteractionResult(interactionResultConsumeField);
            }
            if (result == EntityInteractionResult.FAIL) {
                return readInteractionResult(interactionResultFailField);
            }
            return readInteractionResult(interactionResultPassField);
        }

        private @NotNull Object readInteractionResult(@NotNull Field field) {
            return ReflectionSupport.readField(field, null);
        }

        private @Nullable ItemStack toBukkitItem(@Nullable Object nativeItem) {
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

        private @Nullable Object toNmsItem(@Nullable ItemStack itemStack) {
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

        private @NotNull EntityEquipmentSlot toApiEquipmentSlot(@NotNull Object equipmentSlot) {
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
        private @NotNull Object toNmsEquipmentSlot(@NotNull EntityEquipmentSlot slot) {
            String name = slot == EntityEquipmentSlot.MAIN_HAND
                    ? "MAINHAND"
                    : slot == EntityEquipmentSlot.OFF_HAND ? "OFFHAND" : slot.name();
            return Enum.valueOf((Class) equipmentSlotType, name);
        }
    }
}
