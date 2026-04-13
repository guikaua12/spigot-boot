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
package tech.guilhermekaua.spigotboot.entity.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.EntityNetworkController;
import tech.guilhermekaua.spigotboot.entity.api.EntityNetworkState;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.RuntimeNativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyTrackingBindingStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class VersionedEntityPlatformTest {

    @Test
    void shouldRegisterTemplatesAndLookupById() {
        VersionedEntityPlatform platform = new VersionedEntityPlatform(
                MinecraftVersion.of(1, 21, 11),
                new RecordingAdapter()
        );
        EntityTemplate<Zombie> template = createTemplate(new ArrayList<String>());

        platform.register(template);

        assertSame(template, platform.template(template.id()));
        assertEquals(1, platform.templates().size());
    }

    @Test
    void shouldSpawnTemplatesThroughSharedLifecycleWithoutMandatoryRegistration() {
        RecordingAdapter adapter = new RecordingAdapter();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        EntityTemplate<Zombie> template = createTemplate(new ArrayList<String>());
        SpawnOptions spawnOptions = SpawnOptions.at(new Location(Mockito.mock(World.class), 10.0D, 64.0D, 12.0D));

        SpawnedEntity<Zombie> entity = platform.spawn(template, spawnOptions);

        assertEquals(template.id(), entity.templateId());
        assertEquals(MinecraftVersion.of(1, 21, 11), entity.minecraftVersion());
        assertSame(entity, adapter.lastSpawnHandle);
        assertSame(template, adapter.lastSpawnTemplate);
    }

    @Test
    void shouldSpawnOneOffEntitiesWithoutRegistrationCeremony() {
        RecordingAdapter adapter = new RecordingAdapter();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        Location location = new Location(Mockito.mock(World.class), 1.0D, 65.0D, -3.0D);

        SpawnedEntity<Zombie> entity = platform.spawn(
                CustomEntityBaseType.ZOMBIE,
                Zombie.class,
                location,
                spawn -> spawn.data("trackedPlayerId", "demo-player")
        );

        assertNull(entity.templateId());
        assertSame(CustomEntityBaseType.ZOMBIE, entity.baseType());
        assertEquals("demo-player", adapter.lastSpawnOptions.data().getRequired("trackedPlayerId", String.class));
        assertNull(adapter.lastSpawnTemplate.id());
    }

    @Test
    void shouldCacheAttachedEntitiesByBukkitIdentity() {
        RecordingAdapter adapter = new RecordingAdapter();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        HandleAwareEntity entity = Mockito.mock(HandleAwareEntity.class);
        @SuppressWarnings("unchecked")
        ControlledEntity<Entity> controlledEntity = Mockito.mock(ControlledEntity.class);
        ExistingControlledNativeHandle nativeHandle = new ExistingControlledNativeHandle(controlledEntity);

        when(entity.getHandle()).thenReturn(nativeHandle);
        when(entity.getType()).thenReturn(EntityType.ARMOR_STAND);
        when(controlledEntity.bukkitEntity()).thenReturn(entity);
        when(controlledEntity.isHooked()).thenReturn(true);
        when(controlledEntity.isRemoved()).thenReturn(false);
        adapter.attachedEntity = controlledEntity;

        ControlledEntity<Entity> first = platform.get(entity);
        ControlledEntity<Entity> second = platform.get(entity);

        assertSame(controlledEntity, first);
        assertSame(first, second);
        assertEquals(0, adapter.attachInvocations);
        assertEquals(0, adapter.paperReplacementInvocations);
    }

    @Test
    void shouldEvictRemovedAttachedEntitiesAndAttachAgain() {
        ReattachingAdapter adapter = new ReattachingAdapter();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        HandleAwareEntity entity = Mockito.mock(HandleAwareEntity.class);

        when(entity.getHandle()).thenReturn(new Object());
        when(entity.getType()).thenReturn(EntityType.ARMOR_STAND);
        when(entity.isValid()).thenReturn(true);

        ControlledEntity<Entity> first = platform.get(entity);
        adapter.lastLifecycle.onNativeHook("remove", new RemoveAwareNativeEntity(), new Object[0]);

        ControlledEntity<Entity> second = platform.get(entity);
        ControlledEntity<Entity> third = platform.get(entity);

        assertNotSame(first, second);
        assertSame(second, third);
        assertEquals(2, adapter.attachInvocations);
    }

    @Test
    void shouldKeepPaperFreshSpawnSeparateFromReplacementAndAttachPaths() {
        DelegatingMetadataAdapter adapter = new DelegatingMetadataAdapter(
                MinecraftVersion.of(1, 21, 11),
                new EntityVersionCapabilities(
                        EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                        true,
                        EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                        EntityWorldRegistrationMode.REFERENCE_REWRITE
                ),
                new EntityVersionBindings(
                        new EntityFreshSpawnBinding(
                                Arrays.asList(
                                        NativeEntityConstructorShape.LEVEL_AND_POSITION,
                                        NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL
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
                )
        );
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);

        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(
                createConstructorFirstTemplate(adapter.paperFreshSpawnEvents),
                SpawnOptions.at(new Location(Mockito.mock(World.class), 6.0D, 64.0D, 6.0D))
        );

        assertEquals(1, adapter.paperFreshSpawnInvocations);
        assertEquals(0, adapter.paperReplacementInvocations);
        assertEquals(0, adapter.attachInvocations);
        assertTrue(adapter.paperReplacementEvents.isEmpty());
        assertSame(adapter.lastTrackerEntryHandle, spawnedEntity.networkState().trackerEntryHandle());
        assertSame(adapter.lastTrackerStateHandle, spawnedEntity.networkState().trackerStateHandle());
        assertEquals(
                Arrays.asList(
                        "prepare",
                        "create-native",
                        "bind-native",
                        "resolve-bukkit",
                        "bind-bukkit",
                        "add-world",
                        "tracking",
                        "initializer",
                        "spawn"
                ),
                adapter.paperFreshSpawnEvents
        );
    }

    @Test
    void shouldKeepLegacyFreshSpawnSeparateFromReplacementAndAttachPaths() {
        DelegatingMetadataAdapter adapter = new DelegatingMetadataAdapter(
                MinecraftVersion.of(1, 8, 8),
                new EntityVersionCapabilities(
                        EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                        false,
                        EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                        EntityWorldRegistrationMode.REFERENCE_REWRITE
                ),
                new EntityVersionBindings(
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
                )
        );
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 8, 8), adapter);

        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(
                createConstructorFirstTemplate(adapter.legacyFreshSpawnEvents),
                SpawnOptions.at(new Location(Mockito.mock(World.class), -6.0D, 70.0D, 3.0D))
        );

        assertEquals(1, adapter.legacyFreshSpawnInvocations);
        assertEquals(0, adapter.legacyReplacementInvocations);
        assertEquals(0, adapter.attachInvocations);
        assertTrue(adapter.legacyReplacementEvents.isEmpty());
        assertSame(adapter.lastTrackerEntryHandle, spawnedEntity.networkState().trackerEntryHandle());
        assertNull(spawnedEntity.networkState().trackerStateHandle());
        assertEquals(
                Arrays.asList(
                        "prepare",
                        "create-native",
                        "bind-native",
                        "resolve-bukkit",
                        "bind-bukkit",
                        "add-world",
                        "tracking",
                        "initializer",
                        "spawn"
                ),
                adapter.legacyFreshSpawnEvents
        );
    }

    @Test
    void shouldExposeRuntimeMetadataFromAdaptersThatProvideIt() {
        VersionedEntityPlatform platform = new VersionedEntityPlatform(
                MinecraftVersion.of(1, 21, 11),
                new RecordingAdapter()
        );

        assertSame(EntityFreshSpawnPath.CONSTRUCTOR_FIRST, platform.capabilities().freshSpawnPath());
        assertTrue(platform.capabilities().trackerStateHandleAvailable());
        assertSame(
                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                platform.capabilities().freshSpawnWorldRegistrationMode()
        );
        assertEquals(
                Arrays.asList(
                        NativeEntityConstructorShape.LEVEL_AND_POSITION,
                        NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL,
                        NativeEntityConstructorShape.LEVEL_ONLY
                ),
                platform.bindings().freshSpawn().constructorPriority()
        );
        assertTrue(platform.bindings().replacement().trackerStateHandleAvailable());
        assertEquals("paper-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("paper-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("paper-chunk-preload-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("paper-entry-and-state", platform.strategies().trackingBinding().id());
    }

    @Test
    void shouldKeepPaperLikeStrategyBundleStableAndExecuteSharedFreshSpawnAndReplacement() {
        DelegatingMetadataAdapter adapter = new DelegatingMetadataAdapter(
                MinecraftVersion.of(1, 21, 11),
                new EntityVersionCapabilities(
                        EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                        true,
                        EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                        EntityWorldRegistrationMode.REFERENCE_REWRITE
                ),
                new EntityVersionBindings(
                        new EntityFreshSpawnBinding(
                                Arrays.asList(
                                        NativeEntityConstructorShape.LEVEL_AND_POSITION,
                                        NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL
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
                )
        );
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        List<String> events = adapter.paperFreshSpawnEvents;
        EntityTemplate<Zombie> template = createConstructorFirstTemplate(events);
        Location location = new Location(Mockito.mock(World.class), 2.0D, 64.0D, 2.0D);
        HandleAwareEntity entity = Mockito.mock(HandleAwareEntity.class);

        when(entity.getHandle()).thenReturn(new Object());
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.isValid()).thenReturn(true);

        assertSame(platform.strategies(), platform.strategies());
        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(template, SpawnOptions.at(location));
        Object spawnedTrackerEntryHandle = spawnedEntity.networkState().trackerEntryHandle();
        Object spawnedTrackerStateHandle = spawnedEntity.networkState().trackerStateHandle();
        ControlledEntity<Entity> attachedEntity = platform.get(entity);

        assertEquals(0, adapter.spawnInvocations);
        assertEquals(1, adapter.paperFreshSpawnInvocations);
        assertEquals(0, adapter.legacyFreshSpawnInvocations);
        assertEquals(1, adapter.paperReplacementInvocations);
        assertEquals(0, adapter.legacyReplacementInvocations);
        assertEquals(0, adapter.attachInvocations);
        assertNull(adapter.lastSpawnLifecycle);
        assertTrue(adapter.lastPaperSpawnLifecycle instanceof RuntimeNativeEntityLifecycle);
        assertTrue(adapter.lastAttachLifecycle instanceof RuntimeAttachedEntityLifecycle);
        assertEquals(
                Arrays.asList(
                        "prepare",
                        "create-native",
                        "bind-native",
                        "resolve-bukkit",
                        "bind-bukkit",
                        "add-world",
                        "tracking",
                        "initializer",
                        "spawn"
                ),
                events
        );
        assertSame(spawnedTrackerEntryHandle, spawnedEntity.networkState().trackerEntryHandle());
        assertSame(spawnedTrackerStateHandle, spawnedEntity.networkState().trackerStateHandle());
        assertSame(adapter.lastTrackerEntryHandle, attachedEntity.networkState().trackerEntryHandle());
        assertSame(adapter.lastTrackerStateHandle, attachedEntity.networkState().trackerStateHandle());
        assertEquals(
                Arrays.asList(
                        "prepare",
                        "allocate",
                        "bind-native",
                        "publish",
                        "resolve-bukkit",
                        "tracking",
                        "repair"
                ),
                adapter.paperReplacementEvents
        );
        assertEquals("paper-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("paper-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("paper-chunk-preload-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("paper-entry-and-state", platform.strategies().trackingBinding().id());
        assertInstanceOf(PaperTrackingBindingStrategy_1_21_plus.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldComposeLegacyStrategyBundleFromCapabilityMetadata() {
        VersionedEntityPlatform platform = new VersionedEntityPlatform(
                MinecraftVersion.of(1, 8, 8),
                new MetadataOnlyAdapter(
                        MinecraftVersion.of(1, 8, 8),
                        new EntityVersionCapabilities(
                                EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                                false,
                                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                                EntityWorldRegistrationMode.REFERENCE_REWRITE
                        ),
                        new EntityVersionBindings(
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
                        )
                )
        );

        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("legacy-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("legacy-constructor-add-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("legacy-entry-only", platform.strategies().trackingBinding().id());
    }

    @Test
    void shouldKeepLegacyStrategyBundleStableAndExecuteSharedSpawnAndReplacement() {
        DelegatingMetadataAdapter adapter = new DelegatingMetadataAdapter(
                MinecraftVersion.of(1, 8, 8),
                new EntityVersionCapabilities(
                        EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                        false,
                        EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                        EntityWorldRegistrationMode.REFERENCE_REWRITE
                ),
                new EntityVersionBindings(
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
                )
        );
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 8, 8), adapter);
        List<String> events = adapter.legacyFreshSpawnEvents;
        EntityTemplate<Zombie> template = createConstructorFirstTemplate(events);
        Location location = new Location(Mockito.mock(World.class), 4.0D, 70.0D, -6.0D);
        HandleAwareEntity entity = Mockito.mock(HandleAwareEntity.class);

        when(entity.getHandle()).thenReturn(new Object());
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.isValid()).thenReturn(true);

        assertSame(platform.strategies(), platform.strategies());
        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(template, SpawnOptions.at(location));
        Object spawnedTrackerEntryHandle = spawnedEntity.networkState().trackerEntryHandle();
        ControlledEntity<Entity> attachedEntity = platform.get(entity);

        assertEquals(0, adapter.spawnInvocations);
        assertEquals(0, adapter.paperFreshSpawnInvocations);
        assertEquals(1, adapter.legacyFreshSpawnInvocations);
        assertEquals(0, adapter.legacyFallbackInvocations);
        assertEquals(0, adapter.paperReplacementInvocations);
        assertEquals(1, adapter.legacyReplacementInvocations);
        assertEquals(0, adapter.attachInvocations);
        assertNull(adapter.lastSpawnLifecycle);
        assertTrue(adapter.lastLegacySpawnLifecycle instanceof RuntimeNativeEntityLifecycle);
        assertTrue(adapter.lastAttachLifecycle instanceof RuntimeAttachedEntityLifecycle);
        assertEquals(
                Arrays.asList(
                        "prepare",
                        "create-native",
                        "bind-native",
                        "resolve-bukkit",
                        "bind-bukkit",
                        "add-world",
                        "tracking",
                        "initializer",
                        "spawn"
                ),
                events
        );
        assertSame(spawnedTrackerEntryHandle, spawnedEntity.networkState().trackerEntryHandle());
        assertNull(spawnedEntity.networkState().trackerStateHandle());
        assertSame(adapter.lastTrackerEntryHandle, attachedEntity.networkState().trackerEntryHandle());
        assertNull(attachedEntity.networkState().trackerStateHandle());
        assertEquals(
                Arrays.asList(
                        "prepare",
                        "allocate",
                        "bind-native",
                        "publish",
                        "resolve-bukkit",
                        "tracking",
                        "repair"
                ),
                adapter.legacyReplacementEvents
        );
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("legacy-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("legacy-constructor-add-and-rewrite", platform.strategies().worldAdd().id());
        assertEquals("legacy-entry-only", platform.strategies().trackingBinding().id());
        assertInstanceOf(LegacyTrackingBindingStrategy_1_8_to_1_12.class, platform.strategies().trackingBinding());
    }

    @Test
    void shouldKeepLegacyPreparationFallbackBehindTheSelectedSharedStrategy() {
        DelegatingMetadataAdapter adapter = new DelegatingMetadataAdapter(
                MinecraftVersion.of(1, 8, 8),
                new EntityVersionCapabilities(
                        EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                        false,
                        EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                        EntityWorldRegistrationMode.REFERENCE_REWRITE
                ),
                new EntityVersionBindings(
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
                )
        );
        adapter.failLegacyPreparation = true;
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 8, 8), adapter);
        List<String> events = adapter.legacyFreshSpawnEvents;
        EntityTemplate<Zombie> template = createConstructorFirstTemplate(events);

        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(
                template,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 9.0D, 70.0D, -2.0D))
        );

        assertEquals(0, adapter.spawnInvocations);
        assertEquals(0, adapter.paperFreshSpawnInvocations);
        assertEquals(1, adapter.legacyFreshSpawnInvocations);
        assertEquals(1, adapter.legacyFallbackInvocations);
        assertNull(adapter.lastSpawnLifecycle);
        assertTrue(adapter.lastLegacySpawnLifecycle instanceof RuntimeNativeEntityLifecycle);
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals(
                Arrays.asList(
                        "prepare",
                        "fallback",
                        "bind-bukkit",
                        "initializer",
                        "spawn"
                ),
                events
        );
        assertSame(adapter.lastTrackerEntryHandle, spawnedEntity.networkState().trackerEntryHandle());
        assertNull(spawnedEntity.networkState().trackerStateHandle());
    }

    @Test
    void shouldFallbackToUnspecifiedRuntimeMetadataForPlainAdapters() {
        VersionedEntityPlatform platform = new VersionedEntityPlatform(
                MinecraftVersion.of(1, 21, 11),
                new ReattachingAdapter()
        );

        assertSame(EntityVersionCapabilities.unspecified(), platform.capabilities());
        assertSame(EntityVersionBindings.unspecified(), platform.bindings());
        assertEquals("unspecified", platform.strategies().freshSpawn().id());
        assertEquals("unspecified", platform.strategies().replacement().id());
        assertEquals("unspecified", platform.strategies().worldAdd().id());
        assertEquals("unspecified", platform.strategies().trackingBinding().id());
    }

    @Test
    void shouldDelegateThroughUnspecifiedStrategyBundleForPlainAdapters() {
        PlainDelegatingAdapter adapter = new PlainDelegatingAdapter();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        EntityTemplate<Zombie> template = createTemplate(new ArrayList<String>());
        HandleAwareEntity entity = Mockito.mock(HandleAwareEntity.class);

        when(entity.getHandle()).thenReturn(new Object());
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.isValid()).thenReturn(true);

        assertSame(platform.strategies(), platform.strategies());
        platform.spawn(template, SpawnOptions.at(new Location(Mockito.mock(World.class), 8.0D, 64.0D, 8.0D)));
        platform.get(entity);

        assertEquals(1, adapter.spawnInvocations);
        assertEquals(1, adapter.attachInvocations);
        assertTrue(adapter.lastSpawnLifecycle instanceof RuntimeNativeEntityLifecycle);
        assertTrue(adapter.lastAttachLifecycle instanceof RuntimeAttachedEntityLifecycle);
        assertEquals("unspecified", platform.strategies().freshSpawn().id());
        assertEquals("unspecified", platform.strategies().replacement().id());
        assertEquals("unspecified", platform.strategies().worldAdd().id());
        assertEquals("unspecified", platform.strategies().trackingBinding().id());
    }

    private static EntityTemplate<Zombie> createTemplate(List<String> events) {
        return EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "orbit"),
                        CustomEntityBaseType.ZOMBIE
                )
                .initialize(entity -> events.add("initializer"))
                .controller(context -> new tech.guilhermekaua.spigotboot.entity.api.EntityController<Zombie>() {
                    @Override
                    public void onSpawn(@NotNull tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity<Zombie> entity) {
                        events.add("spawn");
                    }
                })
                .build();
    }

    private static EntityTemplate<Zombie> createConstructorFirstTemplate(List<String> events) {
        return EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "paper-orbit"),
                        CustomEntityBaseType.ZOMBIE
                )
                .networkController(context -> new EntityNetworkController<Zombie>() {
                    @Override
                    public void onBind(@NotNull tech.guilhermekaua.spigotboot.entity.api.ControlledEntity<Zombie> entity,
                                       @NotNull EntityNetworkState state) {
                        events.add("bind-bukkit");
                    }
                })
                .initialize(entity -> events.add("initializer"))
                .controller(context -> new tech.guilhermekaua.spigotboot.entity.api.EntityController<Zombie>() {
                    @Override
                    public void onSpawn(@NotNull tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity<Zombie> entity) {
                        events.add("spawn");
                    }
                })
                .build();
    }

    private static HandleAwareZombie createHandleAwareZombie() {
        HandleAwareZombie zombie = Mockito.mock(HandleAwareZombie.class);
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getHandle()).thenReturn(new Object());
        return zombie;
    }

    private static BaseReplacementNativeHandle resolveSharedReplacementHandle(Entity entity) {
        if (entity instanceof HandleAwareEntity) {
            Object handle = ((HandleAwareEntity) entity).getHandle();
            if (handle instanceof BaseReplacementNativeHandle) {
                return (BaseReplacementNativeHandle) handle;
            }
        }
        return new BaseReplacementNativeHandle();
    }

    private static final class RecordingAdapter
            implements EntityVersionAdapter,
            EntityVersionMetadataProvider,
            PaperFreshSpawnStrategy_1_21_plus.Provider,
            PaperReplacementStrategy_1_21_plus.Provider {
        private static final EntityVersionCapabilities CAPABILITIES = new EntityVersionCapabilities(
                EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
                true,
                EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                EntityWorldRegistrationMode.REFERENCE_REWRITE
        );
        private static final EntityVersionBindings BINDINGS = new EntityVersionBindings(
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
        private SpawnedEntity<Zombie> lastSpawnHandle;
        private EntityTemplate<?> lastSpawnTemplate;
        private SpawnOptions lastSpawnOptions;
        private ControlledEntity<?> attachedEntity;
        private Entity lastReplacementBukkitEntity;
        private int attachInvocations;
        private int paperReplacementInvocations;

        @Override
        public MinecraftVersion minimumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public MinecraftVersion maximumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public @NotNull EntityVersionCapabilities entityCapabilities() {
            return CAPABILITIES;
        }

        @Override
        public @NotNull EntityVersionBindings entityBindings() {
            return BINDINGS;
        }

        @Override
        public boolean supports(CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> SpawnedEntity<T> spawn(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                NativeEntityLifecycle<T> lifecycle
        ) {
            HandleAwareZombie zombie = createHandleAwareZombie();

            lifecycle.bind((T) zombie);
            lifecycle.onSpawn();

            lastSpawnTemplate = template;
            lastSpawnOptions = spawnOptions;
            lastSpawnHandle = (SpawnedEntity<Zombie>) lifecycle.handle();
            return (SpawnedEntity<T>) lifecycle.handle();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            attachInvocations++;
            return (ControlledEntity<T>) attachedEntity;
        }

        @Override
        public @NotNull PaperFreshSpawnStrategy_1_21_plus.Support paperFreshSpawnSupport() {
            return new RecordingPaperFreshSpawnSupport(this);
        }

        @Override
        public @NotNull PaperReplacementStrategy_1_21_plus.Support paperReplacementSupport() {
            return new RecordingPaperReplacementSupport(this);
        }
    }

    private static final class RecordingPaperFreshSpawnSupport implements PaperFreshSpawnStrategy_1_21_plus.Support {
        private final RecordingAdapter owner;

        private RecordingPaperFreshSpawnSupport(RecordingAdapter owner) {
            this.owner = owner;
        }

        @Override
        public <T extends Entity> PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn prepareFreshSpawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions
        ) {
            owner.lastSpawnTemplate = template;
            owner.lastSpawnOptions = spawnOptions;
            return new PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn(createHandleAwareZombie());
        }

        @Override
        public @NotNull Object createNativeEntity(
                @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                @NotNull Location location
        ) {
            return preparedSpawn.preparedMetadata();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> void bindLifecycleToNativeEntity(
                @NotNull Object nativeEntity,
                @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            owner.lastSpawnHandle = (SpawnedEntity<Zombie>) lifecycle.handle();
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
            return (Entity) nativeEntity;
        }

        @Override
        public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
            return new PaperFreshWorldHandle();
        }

        @Override
        public @NotNull PaperFreshSpawnStrategy_1_21_plus.TrackingHandles resolveTrackingHandles(
                @NotNull Object nativeEntity
        ) {
            return new PaperFreshSpawnStrategy_1_21_plus.TrackingHandles(null, null);
        }
    }

    private static final class RecordingPaperReplacementSupport implements PaperReplacementStrategy_1_21_plus.Support {
        private final RecordingAdapter owner;

        private RecordingPaperReplacementSupport(RecordingAdapter owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
            return resolveSharedReplacementHandle(entity);
        }

        @Override
        @NotNull
        public <T extends Entity> PaperReplacementStrategy_1_21_plus.PreparedReplacement prepareReplacement(
                @NotNull T entity,
                @NotNull Object currentNativeHandle
        ) {
            owner.paperReplacementInvocations++;
            owner.lastReplacementBukkitEntity = entity;
            return new PaperReplacementStrategy_1_21_plus.PreparedReplacement(currentNativeHandle);
        }

        @Override
        public @NotNull Object allocateReplacementHandle(
                @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
        ) {
            return new GeneratedReplacementNativeHandle();
        }

        @Override
        public <T extends Entity> void bindLifecycleToReplacement(
                @NotNull Object replacementHandle,
                @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
        }

        @Override
        public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
        }

        @Override
        public void rebindModernBukkitBridge(
                @NotNull Entity entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void replaceModernWorldReferences(@NotNull Object currentNativeHandle, @NotNull Object replacementHandle) {
        }

        @Override
        public void rewireModernVehicleAndPassengerReferences(
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void refreshModernBukkitWrappers(@NotNull Entity entity) {
        }

        @Override
        public void markModernEntityRemoved(@NotNull Object currentNativeHandle) {
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
            return owner.lastReplacementBukkitEntity;
        }

        @Override
        public @NotNull PaperReplacementStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
                @NotNull Object replacementHandle
        ) {
            return new PaperReplacementStrategy_1_21_plus.TrackingHandles(null, null);
        }

        @Override
        public <T extends Entity> void scheduleRepairPass(
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull T entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }
    }

    private static final class ReattachingAdapter implements EntityVersionAdapter {
        private int attachInvocations;
        private RuntimeAttachedEntityLifecycle<Entity> lastLifecycle;

        @Override
        public MinecraftVersion minimumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public MinecraftVersion maximumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public boolean supports(CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ARMOR_STAND;
        }

        @Override
        public <T extends Entity> SpawnedEntity<T> spawn(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            attachInvocations++;
            RuntimeAttachedEntityLifecycle<T> attachedLifecycle = (RuntimeAttachedEntityLifecycle<T>) lifecycle;
            attachedLifecycle.bindHookBinder(new RemoveOnlyHookBinder<T>());
            attachedLifecycle.bind(entity);
            lastLifecycle = (RuntimeAttachedEntityLifecycle<Entity>) attachedLifecycle;
            return attachedLifecycle.handle();
        }
    }

    private static final class MetadataOnlyAdapter implements EntityVersionAdapter, EntityVersionMetadataProvider {
        private final MinecraftVersion version;
        private final EntityVersionCapabilities capabilities;
        private final EntityVersionBindings bindings;

        private MetadataOnlyAdapter(
                MinecraftVersion version,
                EntityVersionCapabilities capabilities,
                EntityVersionBindings bindings
        ) {
            this.version = version;
            this.capabilities = capabilities;
            this.bindings = bindings;
        }

        @Override
        public MinecraftVersion minimumVersion() {
            return version;
        }

        @Override
        public MinecraftVersion maximumVersion() {
            return version;
        }

        @Override
        public @NotNull EntityVersionCapabilities entityCapabilities() {
            return capabilities;
        }

        @Override
        public @NotNull EntityVersionBindings entityBindings() {
            return bindings;
        }

        @Override
        public boolean supports(CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        public <T extends Entity> SpawnedEntity<T> spawn(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                NativeEntityLifecycle<T> lifecycle
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Entity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class DelegatingMetadataAdapter implements EntityVersionAdapter,
            EntityVersionMetadataProvider,
            PaperFreshSpawnStrategy_1_21_plus.Provider,
            LegacyFreshSpawnStrategy_1_8_to_1_12.Provider,
            PaperReplacementStrategy_1_21_plus.Provider,
            LegacyReplacementStrategy_1_8_to_1_12.Provider {
        private final MinecraftVersion version;
        private final EntityVersionCapabilities capabilities;
        private final EntityVersionBindings bindings;
        private final List<String> paperFreshSpawnEvents = new ArrayList<String>();
        private final List<String> legacyFreshSpawnEvents = new ArrayList<String>();
        private final List<String> paperReplacementEvents = new ArrayList<String>();
        private final List<String> legacyReplacementEvents = new ArrayList<String>();
        private int spawnInvocations;
        private int paperFreshSpawnInvocations;
        private int legacyFreshSpawnInvocations;
        private int legacyFallbackInvocations;
        private int paperReplacementInvocations;
        private int legacyReplacementInvocations;
        private int attachInvocations;
        private Entity lastReplacementBukkitEntity;
        private NativeEntityLifecycle<?> lastSpawnLifecycle;
        private NativeEntityLifecycle<?> lastPaperSpawnLifecycle;
        private NativeEntityLifecycle<?> lastLegacySpawnLifecycle;
        private NativeEntityLifecycle<?> lastAttachLifecycle;
        private Object lastTrackerEntryHandle;
        private Object lastTrackerStateHandle;
        private boolean failLegacyPreparation;

        private DelegatingMetadataAdapter(
                MinecraftVersion version,
                EntityVersionCapabilities capabilities,
                EntityVersionBindings bindings
        ) {
            this.version = version;
            this.capabilities = capabilities;
            this.bindings = bindings;
        }

        @Override
        public MinecraftVersion minimumVersion() {
            return version;
        }

        @Override
        public MinecraftVersion maximumVersion() {
            return version;
        }

        @Override
        public @NotNull EntityVersionCapabilities entityCapabilities() {
            return capabilities;
        }

        @Override
        public @NotNull EntityVersionBindings entityBindings() {
            return bindings;
        }

        @Override
        public boolean supports(CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> SpawnedEntity<T> spawn(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                NativeEntityLifecycle<T> lifecycle
        ) {
            spawnInvocations++;
            lastSpawnLifecycle = lifecycle;
            HandleAwareZombie zombie = createHandleAwareZombie();
            lifecycle.bind((T) zombie);
            lifecycle.onSpawn();
            return (SpawnedEntity<T>) lifecycle.handle();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            attachInvocations++;
            lastAttachLifecycle = lifecycle;
            RuntimeAttachedEntityLifecycle<T> attachedLifecycle = (RuntimeAttachedEntityLifecycle<T>) lifecycle;
            attachedLifecycle.bindHookBinder(new RemoveOnlyHookBinder<T>());
            attachedLifecycle.bind(entity);
            return attachedLifecycle.handle();
        }

        @Override
        public @NotNull PaperFreshSpawnStrategy_1_21_plus.Support paperFreshSpawnSupport() {
            return new DelegatingPaperFreshSpawnSupport(this);
        }

        @Override
        public @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.Support legacyFreshSpawnSupport() {
            return new DelegatingLegacyFreshSpawnSupport(this);
        }

        @Override
        public @NotNull PaperReplacementStrategy_1_21_plus.Support paperReplacementSupport() {
            return new DelegatingPaperReplacementSupport(this);
        }

        @Override
        public @NotNull LegacyReplacementStrategy_1_8_to_1_12.Support legacyReplacementSupport() {
            return new DelegatingLegacyReplacementSupport(this);
        }
    }

    private static final class DelegatingPaperFreshSpawnSupport implements PaperFreshSpawnStrategy_1_21_plus.Support {
        private final DelegatingMetadataAdapter owner;

        private DelegatingPaperFreshSpawnSupport(DelegatingMetadataAdapter owner) {
            this.owner = owner;
        }

        @Override
        public <T extends Entity> PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn prepareFreshSpawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions
        ) {
            owner.paperFreshSpawnInvocations++;
            owner.paperFreshSpawnEvents.add("prepare");
            return new PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn(createHandleAwareZombie());
        }

        @Override
        public @NotNull Object createNativeEntity(
                @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                @NotNull Location location
        ) {
            owner.paperFreshSpawnEvents.add("create-native");
            return preparedSpawn.preparedMetadata();
        }

        @Override
        public <T extends Entity> void bindLifecycleToNativeEntity(
                @NotNull Object nativeEntity,
                @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            owner.paperFreshSpawnEvents.add("bind-native");
            owner.lastPaperSpawnLifecycle = lifecycle;
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
            owner.paperFreshSpawnEvents.add("resolve-bukkit");
            return (Entity) nativeEntity;
        }

        @Override
        public void beforeWorldAdd(@NotNull Object nativeEntity, @NotNull Location location) {
            owner.paperFreshSpawnEvents.add("add-world");
        }

        @Override
        public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
            return new PaperFreshWorldHandle();
        }

        @Override
        public @NotNull PaperFreshSpawnStrategy_1_21_plus.TrackingHandles resolveTrackingHandles(
                @NotNull Object nativeEntity
        ) {
            owner.paperFreshSpawnEvents.add("tracking");
            owner.lastTrackerEntryHandle = new Object();
            owner.lastTrackerStateHandle = new Object();
            return new PaperFreshSpawnStrategy_1_21_plus.TrackingHandles(
                    owner.lastTrackerEntryHandle,
                    owner.lastTrackerStateHandle
            );
        }
    }

    private static final class DelegatingLegacyFreshSpawnSupport implements LegacyFreshSpawnStrategy_1_8_to_1_12.Support {
        private final DelegatingMetadataAdapter owner;

        private DelegatingLegacyFreshSpawnSupport(DelegatingMetadataAdapter owner) {
            this.owner = owner;
        }

        @Override
        public <T extends Entity> LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn prepareFreshSpawn(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions
        ) {
            owner.legacyFreshSpawnInvocations++;
            owner.legacyFreshSpawnEvents.add("prepare");
            if (owner.failLegacyPreparation) {
                throw new IllegalStateException("legacy prepare failed");
            }
            return new LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn(createHandleAwareZombie());
        }

        @Override
        public @NotNull Object createNativeEntity(
                @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
                @NotNull Location location
        ) {
            owner.legacyFreshSpawnEvents.add("create-native");
            return preparedSpawn.preparedMetadata();
        }

        @Override
        public <T extends Entity> void bindLifecycleToNativeEntity(
                @NotNull Object nativeEntity,
                @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            owner.legacyFreshSpawnEvents.add("bind-native");
            owner.lastLegacySpawnLifecycle = lifecycle;
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
            owner.legacyFreshSpawnEvents.add("resolve-bukkit");
            return (Entity) nativeEntity;
        }

        @Override
        public void beforeWorldAdd(@NotNull Object nativeEntity, @NotNull Location location) {
            owner.legacyFreshSpawnEvents.add("add-world");
        }

        @Override
        public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
            return new LegacyFreshWorldHandle();
        }

        @Override
        public Object resolveTrackerEntryHandle(@NotNull Object nativeEntity) {
            owner.legacyFreshSpawnEvents.add("tracking");
            owner.lastTrackerEntryHandle = new Object();
            owner.lastTrackerStateHandle = null;
            return owner.lastTrackerEntryHandle;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> @NotNull SpawnedEntity<T> recoverPreparationFailure(
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions,
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull RuntimeException cause
        ) {
            owner.legacyFallbackInvocations++;
            owner.legacyFreshSpawnEvents.add("fallback");
            owner.lastLegacySpawnLifecycle = lifecycle;
            owner.lastTrackerEntryHandle = new Object();
            owner.lastTrackerStateHandle = null;
            HandleAwareZombie zombie = createHandleAwareZombie();
            lifecycle.bind((T) zombie);
            lifecycle.handle().networkState().setTrackerEntryHandle(owner.lastTrackerEntryHandle);
            lifecycle.onSpawn();
            return (SpawnedEntity<T>) lifecycle.handle();
        }
    }

    private static final class DelegatingPaperReplacementSupport implements PaperReplacementStrategy_1_21_plus.Support {
        private final DelegatingMetadataAdapter owner;

        private DelegatingPaperReplacementSupport(DelegatingMetadataAdapter owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
            return resolveSharedReplacementHandle(entity);
        }

        @Override
        @NotNull
        public <T extends Entity> PaperReplacementStrategy_1_21_plus.PreparedReplacement prepareReplacement(
                @NotNull T entity,
                @NotNull Object currentNativeHandle
        ) {
            owner.paperReplacementInvocations++;
            owner.paperReplacementEvents.add("prepare");
            owner.lastReplacementBukkitEntity = entity;
            return new PaperReplacementStrategy_1_21_plus.PreparedReplacement(currentNativeHandle);
        }

        @Override
        public @NotNull Object allocateReplacementHandle(
                @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
        ) {
            owner.paperReplacementEvents.add("allocate");
            return new GeneratedReplacementNativeHandle();
        }

        @Override
        public <T extends Entity> void bindLifecycleToReplacement(
                @NotNull Object replacementHandle,
                @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            owner.paperReplacementEvents.add("bind-native");
            owner.lastAttachLifecycle = lifecycle;
        }

        @Override
        public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
            owner.paperReplacementEvents.add("publish");
        }

        @Override
        public void rebindModernBukkitBridge(
                @NotNull Entity entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void replaceModernWorldReferences(@NotNull Object currentNativeHandle, @NotNull Object replacementHandle) {
        }

        @Override
        public void rewireModernVehicleAndPassengerReferences(
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void refreshModernBukkitWrappers(@NotNull Entity entity) {
        }

        @Override
        public void markModernEntityRemoved(@NotNull Object currentNativeHandle) {
        }

        @Override
        public @NotNull PaperReplacementStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
                @NotNull Object replacementHandle
        ) {
            owner.paperReplacementEvents.add("tracking");
            owner.lastTrackerEntryHandle = new Object();
            owner.lastTrackerStateHandle = new Object();
            return new PaperReplacementStrategy_1_21_plus.TrackingHandles(
                    owner.lastTrackerEntryHandle,
                    owner.lastTrackerStateHandle
            );
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
            owner.paperReplacementEvents.add("resolve-bukkit");
            return owner.lastReplacementBukkitEntity;
        }

        @Override
        public <T extends Entity> void scheduleRepairPass(
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull T entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
            owner.paperReplacementEvents.add("repair");
        }
    }

    private static final class DelegatingLegacyReplacementSupport implements LegacyReplacementStrategy_1_8_to_1_12.Support {
        private final DelegatingMetadataAdapter owner;

        private DelegatingLegacyReplacementSupport(DelegatingMetadataAdapter owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
            return resolveSharedReplacementHandle(entity);
        }

        @Override
        @NotNull
        public <T extends Entity> LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement prepareReplacement(
                @NotNull T entity,
                @NotNull Object currentNativeHandle
        ) {
            owner.legacyReplacementInvocations++;
            owner.legacyReplacementEvents.add("prepare");
            owner.lastReplacementBukkitEntity = entity;
            return new LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement(currentNativeHandle);
        }

        @Override
        public @NotNull Object allocateReplacementHandle(
                @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
        ) {
            owner.legacyReplacementEvents.add("allocate");
            return new GeneratedReplacementNativeHandle();
        }

        @Override
        public <T extends Entity> void bindLifecycleToReplacement(
                @NotNull Object replacementHandle,
                @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            owner.legacyReplacementEvents.add("bind-native");
            owner.lastAttachLifecycle = lifecycle;
        }

        @Override
        public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
            owner.legacyReplacementEvents.add("publish");
        }

        @Override
        public void rebindLegacyBukkitBridge(
                @NotNull Entity entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void replaceLegacyWorldReferences(@NotNull Object currentNativeHandle, @NotNull Object replacementHandle) {
        }

        @Override
        public void rewireLegacyVehicleAndPassengerReferences(
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void refreshLegacyBukkitWrappers(@NotNull Entity entity) {
        }

        @Override
        public void markLegacyEntityRemoved(@NotNull Object currentNativeHandle) {
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
            owner.legacyReplacementEvents.add("resolve-bukkit");
            return owner.lastReplacementBukkitEntity;
        }

        @Override
        public Object resolveTrackerEntryHandle(@NotNull Object replacementHandle) {
            owner.legacyReplacementEvents.add("tracking");
            owner.lastTrackerEntryHandle = new Object();
            owner.lastTrackerStateHandle = null;
            return owner.lastTrackerEntryHandle;
        }

        @Override
        public <T extends Entity> void scheduleRepairPass(
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull T entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
            owner.legacyReplacementEvents.add("repair");
        }
    }

    private static final class PlainDelegatingAdapter implements EntityVersionAdapter {
        private int spawnInvocations;
        private int attachInvocations;
        private NativeEntityLifecycle<?> lastSpawnLifecycle;
        private NativeEntityLifecycle<?> lastAttachLifecycle;

        @Override
        public MinecraftVersion minimumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public MinecraftVersion maximumVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public boolean supports(CustomEntityBaseType baseType) {
            return baseType == CustomEntityBaseType.ZOMBIE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> SpawnedEntity<T> spawn(
                EntityTemplate<T> template,
                SpawnOptions spawnOptions,
                NativeEntityLifecycle<T> lifecycle
        ) {
            spawnInvocations++;
            lastSpawnLifecycle = lifecycle;
            HandleAwareZombie zombie = Mockito.mock(HandleAwareZombie.class);
            when(zombie.isValid()).thenReturn(true);
            when(zombie.getHandle()).thenReturn(new Object());
            lifecycle.bind((T) zombie);
            lifecycle.onSpawn();
            return (SpawnedEntity<T>) lifecycle.handle();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            attachInvocations++;
            lastAttachLifecycle = lifecycle;
            RuntimeAttachedEntityLifecycle<T> attachedLifecycle = (RuntimeAttachedEntityLifecycle<T>) lifecycle;
            attachedLifecycle.bindHookBinder(new RemoveOnlyHookBinder<T>());
            attachedLifecycle.bind(entity);
            return attachedLifecycle.handle();
        }
    }

    private static final class PaperFreshWorldHandle {
        public boolean addFreshEntity(Object entity) {
            return true;
        }
    }

    private static final class LegacyFreshWorldHandle {
        public boolean addEntity(Object entity) {
            return true;
        }
    }

    private static final class RemoveOnlyHookBinder<T extends Entity> implements NativeHookBinder<T> {
        @Override
        public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs(@NotNull Class<?> nativeType) {
            return java.util.Collections.emptyList();
        }

        @Override
        public Object dispatch(
                @NotNull AbstractRuntimeControlledEntity<T> controlledEntity,
                @NotNull LifecycleAwareNativeEntity nativeEntity,
                @NotNull String hookName,
                Object[] arguments
        ) {
            if (!"remove".equals(hookName)) {
                return null;
            }
            controlledEntity.dispatchRemove(new ContextualBaseInvoker<tech.guilhermekaua.spigotboot.entity.api.EntityRemoveContext<T>, Void>() {
                @Override
                public Void invoke(@NotNull tech.guilhermekaua.spigotboot.entity.api.EntityRemoveContext<T> context) {
                    nativeEntity.spigotBootInvokeBase("remove", new Object[0]);
                    return null;
                }
            });
            return null;
        }
    }

    private static final class RemoveAwareNativeEntity implements LifecycleAwareNativeEntity {
        private NativeEntityLifecycle<?> lifecycle;

        @Override
        public void spigotBootBindLifecycle(@NotNull NativeEntityLifecycle<?> lifecycle) {
            this.lifecycle = lifecycle;
        }

        @Override
        public NativeEntityLifecycle<?> spigotBootGetLifecycle() {
            return lifecycle;
        }

        @Override
        public Object spigotBootInvokeBase(@NotNull String hookName, Object[] arguments) {
            return null;
        }
    }

    private static class BaseReplacementNativeHandle {
        @SuppressWarnings("unused")
        private Object marker = new Object();
    }

    private static final class GeneratedReplacementNativeHandle extends BaseReplacementNativeHandle {
    }

    private static final class ExistingControlledNativeHandle extends BaseReplacementNativeHandle
            implements LifecycleAwareNativeEntity {
        private final NativeEntityLifecycle<?> lifecycle;

        @SuppressWarnings("unchecked")
        private ExistingControlledNativeHandle(ControlledEntity<?> controlledEntity) {
            NativeEntityLifecycle<Entity> existingLifecycle = Mockito.mock(NativeEntityLifecycle.class);
            when(existingLifecycle.handle()).thenReturn((ControlledEntity<Entity>) controlledEntity);
            this.lifecycle = existingLifecycle;
        }

        @Override
        public void spigotBootBindLifecycle(@NotNull NativeEntityLifecycle<?> lifecycle) {
        }

        @Override
        public NativeEntityLifecycle<?> spigotBootGetLifecycle() {
            return lifecycle;
        }

        @Override
        public Object spigotBootInvokeBase(@NotNull String hookName, Object[] arguments) {
            return null;
        }
    }

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }

    private interface HandleAwareEntity extends Entity {
        Object getHandle();
    }
}
