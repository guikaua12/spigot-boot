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
package tech.guilhermekaua.spigotboot.v1_8_8.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
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
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.versions.runtime.controller.LogicalEntityHook;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityStrategyBundleSelector;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.VersionEntrypoint;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.EntityStrategyBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Version-local spawn and attach entrypoint for Minecraft 1.8.8.
 *
 * @since 2.0.2
 */
public final class EntityFactoryV1_8_8
        implements VersionEntrypoint,
        LegacyFreshSpawnStrategy_1_8_to_1_12.Support,
        LegacyReplacementStrategy_1_8_to_1_12.Support {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 8, 8);
    private static final String SUPPORTED_FAMILY = "1.8.8";
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
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUDED_BASE_TYPES = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    private static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUDED_BASE_TYPES =
            EnumSet.noneOf(CustomEntityBaseType.class);
    private static final EnumSet<CustomEntityBaseType> ADVERTISED_SUPPORTED_BASE_TYPES = EnumSet.of(
            CustomEntityBaseType.ITEM,
            CustomEntityBaseType.EXPERIENCE_ORB,
            CustomEntityBaseType.LEASH_KNOT,
            CustomEntityBaseType.PAINTING,
            CustomEntityBaseType.ARROW,
            CustomEntityBaseType.SNOWBALL,
            CustomEntityBaseType.FIREBALL,
            CustomEntityBaseType.SMALL_FIREBALL,
            CustomEntityBaseType.ENDER_PEARL,
            CustomEntityBaseType.EYE_OF_ENDER,
            CustomEntityBaseType.EXPERIENCE_BOTTLE,
            CustomEntityBaseType.ITEM_FRAME,
            CustomEntityBaseType.WITHER_SKULL,
            CustomEntityBaseType.TNT,
            CustomEntityBaseType.FALLING_BLOCK,
            CustomEntityBaseType.FIREWORK_ROCKET,
            CustomEntityBaseType.ARMOR_STAND,
            CustomEntityBaseType.COMMAND_BLOCK_MINECART,
            CustomEntityBaseType.BOAT,
            CustomEntityBaseType.MINECART,
            CustomEntityBaseType.CHEST_MINECART,
            CustomEntityBaseType.FURNACE_MINECART,
            CustomEntityBaseType.TNT_MINECART,
            CustomEntityBaseType.HOPPER_MINECART,
            CustomEntityBaseType.SPAWNER_MINECART,
            CustomEntityBaseType.CREEPER,
            CustomEntityBaseType.SKELETON,
            CustomEntityBaseType.SPIDER,
            CustomEntityBaseType.GIANT,
            CustomEntityBaseType.ZOMBIE,
            CustomEntityBaseType.SLIME,
            CustomEntityBaseType.GHAST,
            CustomEntityBaseType.ZOMBIFIED_PIGLIN,
            CustomEntityBaseType.ENDERMAN,
            CustomEntityBaseType.CAVE_SPIDER,
            CustomEntityBaseType.SILVERFISH,
            CustomEntityBaseType.BLAZE,
            CustomEntityBaseType.MAGMA_CUBE,
            CustomEntityBaseType.ENDER_DRAGON,
            CustomEntityBaseType.WITHER,
            CustomEntityBaseType.BAT,
            CustomEntityBaseType.WITCH,
            CustomEntityBaseType.ENDERMITE,
            CustomEntityBaseType.GUARDIAN,
            CustomEntityBaseType.PIG,
            CustomEntityBaseType.SHEEP,
            CustomEntityBaseType.COW,
            CustomEntityBaseType.CHICKEN,
            CustomEntityBaseType.SQUID,
            CustomEntityBaseType.WOLF,
            CustomEntityBaseType.MOOSHROOM,
            CustomEntityBaseType.SNOW_GOLEM,
            CustomEntityBaseType.OCELOT,
            CustomEntityBaseType.IRON_GOLEM,
            CustomEntityBaseType.HORSE,
            CustomEntityBaseType.RABBIT,
            CustomEntityBaseType.VILLAGER,
            CustomEntityBaseType.END_CRYSTAL,
            CustomEntityBaseType.POTION,
            CustomEntityBaseType.EGG,
            CustomEntityBaseType.FISHING_BOBBER,
            CustomEntityBaseType.LIGHTNING_BOLT
    );
    private static final Map<CustomEntityBaseType, EntityMetadata> METADATA_REGISTRY = createMetadataRegistry();

    private final GeneratedNativeEntityClassFactory classFactory = new GeneratedNativeEntityClassFactory();
    private final EntityHookBinderV1_8_8 hookBinder = new EntityHookBinderV1_8_8();
    private final Map<CustomEntityBaseType, EntityMetadata> metadataRegistry = METADATA_REGISTRY;
    private final Map<CustomEntityBaseType, ResolvedSpawnMetadata> spawnMetadataRegistry =
            new LinkedHashMap<CustomEntityBaseType, ResolvedSpawnMetadata>();
    private final Map<Class<?>, ResolvedEntityTypeMetadata> generatedTypes =
            new LinkedHashMap<Class<?>, ResolvedEntityTypeMetadata>();
    /**
     * Returns the runtime capability summary for Minecraft 1.8.8.
     *
     * @return the runtime capability summary
     */
    public static @NotNull VersionCapabilities entityCapabilities() {
        return ENTITY_CAPABILITIES;
    }

    /**
     * Returns the runtime binding bundle for Minecraft 1.8.8.
     *
     * @return the runtime binding bundle
     */
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
                    "Minecraft 1.8.8 does not expose any supported hooks for base type '" + metadata.baseType() + "'."
            );
        }

        Class<?> generatedType = classFactory.createSubclass(
                nativeType,
                generatedClassName(metadata, nativeType),
                hookSpecs
        );
        ensureTrackerMappings(nativeType, generatedType);

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
    public <T extends Entity> LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn prepareFreshSpawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        try {
            EntityMetadata metadata = requireMetadata(template.baseType());
            return new LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn(
                    resolveSpawnMetadata(metadata, spawnOptions.location())
            );
        } catch (RuntimeException exception) {
            throw new LegacyFreshSpawnPreparationException(
                    "Constructor-first legacy fresh-spawn preparation failed before world registration.",
                    exception
            );
        }
    }

    @Override
    public @NotNull Object createNativeEntity(
            @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
            @NotNull Location location
    ) {
        Objects.requireNonNull(preparedSpawn, "preparedSpawn cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        try {
            return createFreshNativeEntity(resolvePreparedSpawnMetadata(preparedSpawn), location);
        } catch (RuntimeException exception) {
            throw new LegacyFreshSpawnPreparationException(
                    "Constructor-first legacy native entity creation failed before world registration.",
                    exception
            );
        }
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
    public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(world.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, world);
    }

    @Override
    public @Nullable Object resolveTrackerEntryHandle(@NotNull Object nativeEntity) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        return resolveLegacyTrackerEntry(nativeEntity);
    }

    @Override
    public <T extends Entity> @NotNull SpawnedEntity<T> recoverPreparationFailure(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle,
            @NotNull RuntimeException cause
        ) {
        return spawnWithReplacementFallback(template, spawnOptions, lifecycle, cause);
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
        CustomEntityBaseType baseType = resolveBaseType(entity);
        requireAdvertisedSupportedBaseType(baseType, "replacement");
        EntityMetadata metadata = requireMetadata(baseType);
        return new LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement(
                resolveGeneratedTypeMetadata(metadata, currentNativeHandle.getClass())
        );
    }

    @Override
    public @NotNull Object allocateReplacementHandle(
            @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
    ) {
        Objects.requireNonNull(preparedReplacement, "preparedReplacement cannot be null");
        return ReflectionSupport.allocateInstance(resolvePreparedReplacementMetadata(preparedReplacement).generatedType());
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
        bindRuntimeLifecycle(replacementHandle, resolvePreparedReplacementMetadata(preparedReplacement), lifecycle);
    }

    @Override
    public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindBukkitZombieInternal(entity, replacementHandle);
    }

    @Override
    public void rebindLegacyBukkitBridge(
            @NotNull Entity entity,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindLegacyBukkitBridgeInternal(entity, currentNativeHandle, replacementHandle);
    }

    @Override
    public void replaceWorldReferences(
            @NotNull EntityPublicationFamily family,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(family, "family cannot be null");
        if (family != EntityPublicationFamily.LEGACY_WORLD_LISTENER) {
            throw new IllegalStateException(
                    "Minecraft 1.8.8 only supports publication family '"
                            + EntityPublicationFamily.LEGACY_WORLD_LISTENER.id()
                            + "' but received '"
                            + family.id()
                            + "'."
            );
        }
        replaceLegacyWorldReferences(currentNativeHandle, replacementHandle);
    }

    @Override
    public void replaceLegacyWorldReferences(@NotNull Object currentNativeHandle, @NotNull Object replacementHandle) {
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        replaceLegacyWorldReferencesInternal(currentNativeHandle, replacementHandle);
    }

    @Override
    public void rewireLegacyVehicleAndPassengerReferences(
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rewireLegacyVehicleAndPassengerReferencesInternal(currentNativeHandle, replacementHandle);
    }

    @Override
    public void refreshLegacyBukkitWrappers(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        refreshLegacyBukkitWrappersInternal(entity);
    }

    @Override
    public void markLegacyEntityRemoved(@NotNull Object currentNativeHandle) {
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        markLegacyEntityRemovedInternal(currentNativeHandle);
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

    private static @NotNull ResolvedEntityTypeMetadata resolvePreparedReplacementMetadata(
            @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
    ) {
        Object preparedMetadata = preparedReplacement.preparedMetadata();
        if (!(preparedMetadata instanceof ResolvedEntityTypeMetadata)) {
            throw new IllegalStateException(
                    "Prepared legacy replacement metadata did not contain a ResolvedEntityTypeMetadata instance."
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
        Object nativeEntity = instantiateNativeEntity(spawnMetadata.resolvedMetadata().generatedType(), levelHandle, location);
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
                "Could not resolve a supported constructor for generated legacy entity type '"
                        + generatedType.getName()
                        + "'."
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
                new String[]{"setPositionRotation", "setLocation"},
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
                new String[]{"setPosition"},
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

        Field yawField = ReflectionSupport.findField(nativeEntity.getClass(), "yaw");
        if (yawField != null) {
            ReflectionSupport.writeField(yawField, nativeEntity, Float.valueOf(location.getYaw()));
        }

        Field pitchField = ReflectionSupport.findField(nativeEntity.getClass(), "pitch");
        if (pitchField != null) {
            ReflectionSupport.writeField(pitchField, nativeEntity, Float.valueOf(location.getPitch()));
        }
    }

    private static @Nullable Object resolveLegacyTrackerEntry(@NotNull Object nativeEntity) {
        Object world = ReflectionSupport.readField(
                ReflectionSupport.requireField(nativeEntity.getClass(), "world"),
                nativeEntity
        );
        int entityId = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(nativeEntity.getClass(), new String[]{"getId"}),
                nativeEntity
        )).intValue();
        return resolveLegacyTrackerEntry(world, entityId);
    }

    private <T extends Entity> @NotNull SpawnedEntity<T> spawnWithReplacementFallback(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle,
            @NotNull RuntimeException cause
    ) {
        EntityMetadata metadata = requireMetadata(template.baseType());
        T entity = template.bukkitType().cast(spawnVanillaEntity(spawnOptions.location(), metadata));
        try {
            ControlledEntity<T> attached = REPLACEMENT_STRATEGY.attach(this, entity, lifecycle);
            lifecycle.onSpawn();
            if (!(attached instanceof SpawnedEntity)) {
                throw new IllegalStateException(
                        "Spawn lifecycle did not return a SpawnedEntity for base type '" + template.baseType() + "'."
                );
            }
            return (SpawnedEntity<T>) attached;
        } catch (RuntimeException exception) {
            exception.addSuppressed(cause);
            entity.remove();
            throw exception;
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

    private static void requireAdvertisedSupportedBaseType(@NotNull CustomEntityBaseType baseType, @NotNull String action) {
        if (!ADVERTISED_SUPPORTED_BASE_TYPES.contains(baseType)) {
            throw new UnsupportedOperationException(
                    "Minecraft " + SUPPORTED_FAMILY + " does not support " + action + " for base type '" + baseType + "'."
            );
        }
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
        if (entityType == null || entityType.getEntityClass() == null) {
            throw new IllegalStateException(
                    "Minecraft " + SUPPORTED_FAMILY + " cannot advertise support for base type '" + baseType
                            + "' because Bukkit EntityType is unavailable."
            );
        }
        return entityType;
    }

    private static @NotNull String generatedClassName(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        return "tech.guilhermekaua.spigotboot.versions.generated.v1_8_8.SpigotBoot"
                + toGeneratedSuffix(metadata.baseType())
                + "V1_8_8_"
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

    private static void ensureTrackerMappings(@NotNull Class<?> zombieSuperclass, @NotNull Class<?> generatedType) {
        Map<Class<?>, String> classToName = resolveEntityTypesMap("d");
        Map<Class<?>, Integer> classToId = resolveEntityTypesMap("f");

        String entityName = classToName.get(zombieSuperclass);
        Integer entityId = classToId.get(zombieSuperclass);
        if (entityName == null || entityId == null) {
            throw new IllegalStateException("Could not resolve the legacy Entity EntityTypes mapping for 1.8.8.");
        }

        classToName.put(generatedType, entityName);
        classToId.put(generatedType, entityId);
    }

    @SuppressWarnings("unchecked")
    private static <T> @NotNull Map<Class<?>, T> resolveEntityTypesMap(@NotNull String fieldName) {
        Class<?> entityTypesClass = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.EntityTypes");
        try {
            Field field = entityTypesClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof Map)) {
                throw new IllegalStateException(
                        "Expected EntityTypes." + fieldName + " to be a Map for Minecraft 1.8.8."
                );
            }
            return (Map<Class<?>, T>) value;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not access EntityTypes." + fieldName + " for Minecraft 1.8.8.",
                    exception
            );
        }
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
        Field bukkitEntityField = ReflectionSupport.findField(oldHandle.getClass(), "bukkitEntity");
        if (bukkitEntityField == null) {
            return;
        }
        ReflectionSupport.writeField(bukkitEntityField, replacementHandle, bukkitEntity);
        ReflectionSupport.writeField(bukkitEntityField, oldHandle, null);
    }

    private static void replaceLegacyWorldReferencesInternal(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Object world = ReflectionSupport.readField(ReflectionSupport.requireField(oldHandle.getClass(), "world"), oldHandle);
        int entityId = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getId"}),
                oldHandle
        )).intValue();

        @SuppressWarnings("unchecked")
        List<Object> entityList = (List<Object>) ReflectionSupport.readField(
                ReflectionSupport.requireField(world.getClass(), "entityList"),
                world
        );
        replaceListEntry(entityList, oldHandle, replacementHandle);

        Object entitiesById = ReflectionSupport.readField(ReflectionSupport.requireField(world.getClass(), "entitiesById"), world);
        ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(entitiesById.getClass(), new String[]{"a"}, int.class, Object.class),
                entitiesById,
                Integer.valueOf(entityId),
                replacementHandle
        );

        if (ReflectionSupport.findField(world.getClass(), "entitiesByUUID") != null) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> entitiesByUuid = (Map<Object, Object>) ReflectionSupport.readField(
                    ReflectionSupport.requireField(world.getClass(), "entitiesByUUID"),
                    world
            );
            Object uuid = ReflectionSupport.readField(ReflectionSupport.requireField(oldHandle.getClass(), "uniqueID"), oldHandle);
            entitiesByUuid.put(uuid, replacementHandle);
        }

        replaceLegacyChunkSlice(world, oldHandle, replacementHandle);
        replaceLegacyTrackerInternal(world, entityId, replacementHandle);
    }

    private static void replaceLegacyChunkSlice(
            @NotNull Object world,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        int chunkX = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getChunkX"}),
                oldHandle
        )).intValue();
        int chunkY = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getChunkY"}),
                oldHandle
        )).intValue();
        int chunkZ = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getChunkZ"}),
                oldHandle
        )).intValue();
        Object chunk = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(world.getClass(), new String[]{"getChunkAt"}, int.class, int.class),
                world,
                Integer.valueOf(chunkX),
                Integer.valueOf(chunkZ)
        );
        @SuppressWarnings("unchecked")
        List<Object>[] entitySlices = (List<Object>[]) ReflectionSupport.readField(
                ReflectionSupport.requireField(chunk.getClass(), "entitySlices"),
                chunk
        );
        if (chunkY < 0 || chunkY >= entitySlices.length) {
            return;
        }
        replaceListEntry(entitySlices[chunkY], oldHandle, replacementHandle);
    }

    private static void replaceLegacyTrackerInternal(
            @NotNull Object world,
            int entityId,
            @NotNull Object replacementHandle
    ) {
        Object trackerEntry = resolveLegacyTrackerEntry(world, entityId);
        if (trackerEntry != null) {
            ReflectionSupport.writeField(
                    ReflectionSupport.requireField(trackerEntry.getClass(), "tracker"),
                    trackerEntry,
                    replacementHandle
            );
        }
    }

    private static @Nullable Object resolveLegacyTrackerEntry(@NotNull Object world, int entityId) {
        Field trackerField = ReflectionSupport.findField(world.getClass(), "tracker");
        if (trackerField == null) {
            return null;
        }

        Object tracker = ReflectionSupport.readField(trackerField, world);
        if (tracker == null) {
            return null;
        }

        Object trackedEntities = ReflectionSupport.readField(
                ReflectionSupport.requireField(tracker.getClass(), "trackedEntities"),
                tracker
        );
        return ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(trackedEntities.getClass(), new String[]{"get"}, int.class),
                trackedEntities,
                Integer.valueOf(entityId)
        );
    }

    private static void rewireLegacyVehicleAndPassengerReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field passengerField = ReflectionSupport.requireField(oldHandle.getClass(), "passenger");
        Field vehicleField = ReflectionSupport.requireField(oldHandle.getClass(), "vehicle");

        Object passenger = ReflectionSupport.readField(passengerField, oldHandle);
        if (passenger != null) {
            ReflectionSupport.writeField(vehicleField, passenger, replacementHandle);
        }

        Object vehicle = ReflectionSupport.readField(vehicleField, oldHandle);
        if (vehicle != null) {
            ReflectionSupport.writeField(passengerField, vehicle, replacementHandle);
        }
    }

    private static void refreshLegacyBukkitWrappersInternal(@NotNull Entity entity) {
        Field equipmentField = ReflectionSupport.findField(entity.getClass(), "equipment");
        if (equipmentField != null) {
            ReflectionSupport.writeField(equipmentField, entity, null);
        }
    }

    private static void markLegacyEntityRemovedInternal(@NotNull Object oldHandle) {
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "dead"), oldHandle, Boolean.TRUE);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "valid"), oldHandle, Boolean.FALSE);
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

    private static void replaceListEntry(@Nullable List<Object> values, @NotNull Object oldValue, @NotNull Object newValue) {
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) == oldValue) {
                values.set(index, newValue);
            }
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

        private ResolvedSpawnMetadata(@NotNull ResolvedEntityTypeMetadata resolvedMetadata) {
            this.resolvedMetadata = Objects.requireNonNull(resolvedMetadata, "resolvedMetadata cannot be null");
        }

        public @NotNull ResolvedEntityTypeMetadata resolvedMetadata() {
            return resolvedMetadata;
        }
    }

    private static final class LegacyFreshSpawnPreparationException extends RuntimeException {
        private LegacyFreshSpawnPreparationException(@NotNull String message, @NotNull Throwable cause) {
            super(message, cause);
        }
    }

    private final class EntityHookBinderV1_8_8 implements NativeHookBinder<Entity> {
        private final Class<?> damageSourceType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.DamageSource");
        private final Class<?> entityHumanType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.EntityHuman");
        private final Class<?> entityType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.Entity");
        private final Class<?> itemStackType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.ItemStack");

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
                Object[] rawArguments = requireArguments(arguments, 3);
                controlledEntity.dispatchMove(
                        ((Double) rawArguments[0]).doubleValue(),
                        ((Double) rawArguments[1]).doubleValue(),
                        ((Double) rawArguments[2]).doubleValue(),
                        new ContextualBaseInvoker<EntityMoveContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityMoveContext<Entity> context) {
                                nativeEntity.spigotBootInvokeBase(
                                        HOOK_MOVE,
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
                Object[] rawArguments = requireArguments(arguments, 2);
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
                Object[] rawArguments = requireArguments(arguments, 1);
                Player player = (Player) resolveBukkitEntity(rawArguments[0]);
                EntityInteractionResult result = controlledEntity.dispatchInteract(
                        player,
                        EntityInteractionHand.MAIN_HAND,
                        new ContextualBaseInvoker<EntityInteractContext<Entity>, EntityInteractionResult>() {
                            @Override
                            public EntityInteractionResult invoke(@NotNull EntityInteractContext<Entity> context) {
                                Object baseResult = nativeEntity.spigotBootInvokeBase(HOOK_INTERACT, new Object[]{rawArguments[0]});
                                return baseResult != null && ((Boolean) baseResult).booleanValue()
                                        ? EntityInteractionResult.SUCCESS
                                        : EntityInteractionResult.PASS;
                            }
                        }
                );
                return Boolean.valueOf(result != EntityInteractionResult.PASS);
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

            if (HOOK_REMOVE.equals(hookName)) {
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
                controlledEntity.dispatchPositionPassenger(
                        resolveLegacyPassenger(nativeEntity),
                        new ContextualBaseInvoker<EntityPositionPassengerContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityPositionPassengerContext<Entity> context) {
                                nativeEntity.spigotBootInvokeBase(HOOK_POSITION_PASSENGER, EMPTY_ARGUMENTS);
                                return null;
                            }
                        }
                );
                return null;
            }

            if (HOOK_INVENTORY_CHANGE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 2);
                final int rawSlot = ((Integer) rawArguments[0]).intValue();
                controlledEntity.dispatchInventoryChange(
                        mapLegacySlot(rawSlot),
                        toBukkitItem(resolveLegacyEquipmentItem(nativeEntity, rawSlot)),
                        toBukkitItem(rawArguments[1]),
                        new ContextualBaseInvoker<EntityInventoryChangeContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityInventoryChangeContext<Entity> context) {
                                nativeEntity.spigotBootInvokeBase(
                                        HOOK_INVENTORY_CHANGE,
                                        new Object[]{
                                                Integer.valueOf(toLegacySlot(context.slot())),
                                                toLegacyItem(context.newItem())
                                        }
                                );
                                return null;
                            }
                        }
                );
                return null;
            }

            throw new IllegalArgumentException("Unknown 1.8.8 Entity hook: " + hookName);
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

        private @NotNull ResolvedHookCatalog resolveHookCatalog(@NotNull Class<?> nativeType) {
            List<GeneratedNativeHookSpec> hookSpecs = new ArrayList<GeneratedNativeHookSpec>();
            EnumSet<LogicalEntityHook> supportedHooks = EnumSet.noneOf(LogicalEntityHook.class);

            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.TICK,
                    HOOK_TICK,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"m"})
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.MOVE,
                    HOOK_MOVE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"move"}, double.class, double.class, double.class)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.PUSH,
                    HOOK_PUSH,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"g"}, double.class, double.class, double.class)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.DAMAGE,
                    HOOK_DAMAGE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"damageEntity"}, damageSourceType, float.class)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.INTERACT,
                    HOOK_INTERACT,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"a"}, entityHumanType)
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
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"die"})
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.COLLIDE,
                    HOOK_COLLIDE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"collide"}, entityType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.POSITION_PASSENGER,
                    HOOK_POSITION_PASSENGER,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"al"})
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.INVENTORY_CHANGE,
                    HOOK_INVENTORY_CHANGE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"setEquipment"}, int.class, itemStackType)
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

        private @Nullable Entity resolveLegacyPassenger(@NotNull LifecycleAwareNativeEntity nativeEntity) {
            Field passengerField = ReflectionSupport.findField(nativeEntity.getClass(), "passenger");
            if (passengerField == null) {
                return null;
            }
            Object passenger = ReflectionSupport.readField(passengerField, nativeEntity);
            return passenger != null ? resolveBukkitEntity(passenger) : null;
        }

        private @Nullable Object resolveLegacyEquipmentItem(
                @NotNull LifecycleAwareNativeEntity nativeEntity,
                int slot
        ) {
            Object equipmentArray = ReflectionSupport.invoke(
                    ReflectionSupport.requireNamedMethod(nativeEntity.getClass(), new String[]{"getEquipment"}),
                    nativeEntity
            );
            if (!(equipmentArray instanceof Object[])) {
                return null;
            }
            Object[] values = (Object[]) equipmentArray;
            return slot >= 0 && slot < values.length ? values[slot] : null;
        }

        private @Nullable ItemStack toBukkitItem(@Nullable Object nativeItem) {
            if (nativeItem == null) {
                return null;
            }
            Class<?> craftItemStackType = ReflectionSupport.requireClass(
                    "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
            );
            Method asBukkitCopyMethod = ReflectionSupport.requireNamedMethod(
                    craftItemStackType,
                    new String[]{"asBukkitCopy"},
                    nativeItem.getClass()
            );
            return (ItemStack) ReflectionSupport.invoke(asBukkitCopyMethod, null, nativeItem);
        }

        private @Nullable Object toLegacyItem(@Nullable ItemStack itemStack) {
            if (itemStack == null) {
                return null;
            }
            Class<?> craftItemStackType = ReflectionSupport.requireClass(
                    "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
            );
            Method asNmsCopyMethod = ReflectionSupport.requireNamedMethod(
                    craftItemStackType,
                    new String[]{"asNMSCopy"},
                    ItemStack.class
            );
            return ReflectionSupport.invoke(asNmsCopyMethod, null, itemStack);
        }

        private @NotNull EntityEquipmentSlot mapLegacySlot(int slot) {
            switch (slot) {
                case 0:
                    return EntityEquipmentSlot.MAIN_HAND;
                case 1:
                    return EntityEquipmentSlot.FEET;
                case 2:
                    return EntityEquipmentSlot.LEGS;
                case 3:
                    return EntityEquipmentSlot.CHEST;
                case 4:
                    return EntityEquipmentSlot.HEAD;
                default:
                    throw new IllegalArgumentException("Unsupported legacy equipment slot index: " + slot);
            }
        }

        private int toLegacySlot(@NotNull EntityEquipmentSlot slot) {
            switch (slot) {
                case MAIN_HAND:
                    return 0;
                case FEET:
                    return 1;
                case LEGS:
                    return 2;
                case CHEST:
                    return 3;
                case HEAD:
                    return 4;
                case OFF_HAND:
                default:
                    throw new IllegalArgumentException("Minecraft 1.8.8 does not support slot " + slot + '.');
            }
        }
    }
}
