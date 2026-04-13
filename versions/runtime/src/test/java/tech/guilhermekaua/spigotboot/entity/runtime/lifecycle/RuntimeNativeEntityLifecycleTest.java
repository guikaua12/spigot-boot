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
package tech.guilhermekaua.spigotboot.entity.runtime.lifecycle;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.EntityController;
import tech.guilhermekaua.spigotboot.entity.api.EntityDamageContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityNetworkController;
import tech.guilhermekaua.spigotboot.entity.api.EntityNetworkState;
import tech.guilhermekaua.spigotboot.entity.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionMetadataProvider;
import tech.guilhermekaua.spigotboot.entity.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperReplacementStrategy_1_21_plus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class RuntimeNativeEntityLifecycleTest {

    @Test
    void shouldPreferContextOverrideOverConvenienceOverride() {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle();
        RecordingNativeEntity nativeEntity = new RecordingNativeEntity();
        List<String> calls = new ArrayList<String>();

        lifecycle.setController(new EntityController<Zombie>() {
            @Override
            public void onTick() {
                calls.add("convenience");
            }

            @Override
            public void onTick(@NotNull EntityTickContext<Zombie> context) {
                calls.add("context");
            }
        });

        lifecycle.onNativeHook("tick", nativeEntity, new Object[0]);

        assertEquals(Arrays.asList("context"), calls);
        assertEquals(0, nativeEntity.tickBaseInvocations);
    }

    @Test
    void shouldOnlyRunBaseWhenControllerInvokesIt() {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle();
        RecordingNativeEntity nativeEntity = new RecordingNativeEntity();

        lifecycle.setController(new EntityController<Zombie>() {
            @Override
            public void onTick() {
            }
        });

        lifecycle.onNativeHook("tick", nativeEntity, new Object[0]);
        assertEquals(0, nativeEntity.tickBaseInvocations);

        lifecycle.clearController();
        lifecycle.onNativeHook("tick", nativeEntity, new Object[0]);
        assertEquals(1, nativeEntity.tickBaseInvocations);
        assertTrue(lifecycle.isHooked());
    }

    @Test
    void shouldStopForwardingTickBaseAfterExplicitRemoval() {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle();
        lifecycle.clearController();

        RecordingNativeEntity nativeEntity = new RecordingNativeEntity();
        lifecycle.onNativeHook("remove", nativeEntity, new Object[0]);
        lifecycle.onNativeHook("tick", nativeEntity, new Object[0]);

        assertEquals(1, nativeEntity.removeBaseInvocations);
        assertEquals(0, nativeEntity.tickBaseInvocations);
        assertTrue(lifecycle.isRemoved());
    }

    @Test
    void shouldReflectRemovalStateForNonLivingEntitiesFromValidity() {
        RuntimeAttachedEntityLifecycle<Entity> lifecycle = new RuntimeAttachedEntityLifecycle<Entity>(
                CustomEntityBaseType.ITEM_FRAME,
                MinecraftVersion.of(1, 21, 11),
                new EntityController<Entity>() {
                }
        );

        Entity entity = Mockito.mock(Entity.class);
        when(entity.isValid()).thenReturn(true, false);
        lifecycle.bind(entity);

        assertFalse(lifecycle.isRemoved());
        assertTrue(lifecycle.isRemoved());
    }

    @Test
    void shouldForwardMutatedArgumentsIntoBaseInvocation() {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle();
        RecordingNativeEntity nativeEntity = new RecordingNativeEntity();

        lifecycle.setController(new EntityController<Zombie>() {
            @Override
            public void onDamage(@NotNull EntityDamageContext<Zombie> context) {
                context.setAmount(7.0F);
                context.base().invoke();
            }
        });

        Object result = lifecycle.onNativeHook("damage", nativeEntity, new Object[]{Float.valueOf(3.0F)});

        assertEquals(Boolean.TRUE, result);
        assertEquals(7.0F, nativeEntity.lastDamageAmount, 0.01F);
    }

    @Test
    void shouldPreserveStateAcrossControllerSwaps() {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle();
        EntityController<Zombie> firstController = new EntityController<Zombie>() {
        };
        EntityController<Zombie> secondController = new EntityController<Zombie>() {
        };

        lifecycle.state().put("marker", "persisted");
        lifecycle.setController(firstController);
        lifecycle.setController(secondController);

        assertEquals("persisted", lifecycle.state().get("marker", String.class));
        assertSame(secondController, lifecycle.controller());
    }

    @Test
    void shouldInstallSpawnedControllerAndRunSpawnTickRemoveHooks() {
        List<String> events = new ArrayList<String>();
        EntityTemplate<Zombie> definition = EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "behavior"),
                        CustomEntityBaseType.ZOMBIE
                )
                .initialize(entity -> events.add("initializer"))
                .controller(context -> new EntityController<Zombie>() {
                    @Override
                    public void onSpawn(@NotNull SpawnedEntity<Zombie> entity) {
                        events.add("spawn");
                    }

                    @Override
                    public void onTick(@NotNull EntityTickContext<Zombie> context) {
                        context.base().invoke();
                        events.add("tick");
                    }

                    @Override
                    public void onRemove(@NotNull EntityRemoveContext<Zombie> context) {
                        try {
                            context.base().invoke();
                        } finally {
                            events.add("remove");
                        }
                    }
                })
                .build();

        RuntimeNativeEntityLifecycle<Zombie> lifecycle = new RuntimeNativeEntityLifecycle<Zombie>(
                definition,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)),
                MinecraftVersion.of(1, 21, 11)
        );
        lifecycle.bindHookBinder(new TestHookBinder<Zombie>());

        Zombie zombie = Mockito.mock(Zombie.class);
        when(zombie.isValid()).thenReturn(true);

        lifecycle.bind(zombie);
        lifecycle.onSpawn();

        RecordingNativeEntity nativeEntity = new RecordingNativeEntity();
        lifecycle.onNativeHook("tick", nativeEntity, new Object[0]);
        lifecycle.onNativeHook("remove", nativeEntity, new Object[0]);

        assertEquals(Arrays.asList("initializer", "spawn", "tick", "remove"), events);
        assertEquals(1, nativeEntity.tickBaseInvocations);
        assertEquals(1, nativeEntity.removeBaseInvocations);
    }

    @Test
    void shouldUseControllerSetDuringInitializerForSpawnCallback() {
        List<String> events = new ArrayList<String>();
        EntityController<Zombie> replacementController = new EntityController<Zombie>() {
            @Override
            public void onSpawn(@NotNull SpawnedEntity<Zombie> entity) {
                events.add("replacement-spawn");
            }
        };

        EntityTemplate<Zombie> definition = EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "spawn-controller-swap"),
                        CustomEntityBaseType.ZOMBIE
                )
                .initialize(entity -> {
                    events.add("initializer");
                    entity.setController(replacementController);
                })
                .controller(context -> new EntityController<Zombie>() {
                    @Override
                    public void onSpawn(@NotNull SpawnedEntity<Zombie> entity) {
                        events.add("factory-spawn");
                    }
                })
                .build();

        RuntimeNativeEntityLifecycle<Zombie> lifecycle = new RuntimeNativeEntityLifecycle<Zombie>(
                definition,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)),
                MinecraftVersion.of(1, 21, 11)
        );
        lifecycle.bindHookBinder(new TestHookBinder<Zombie>());

        Zombie zombie = Mockito.mock(Zombie.class);
        when(zombie.isValid()).thenReturn(true);

        lifecycle.bind(zombie);
        lifecycle.onSpawn();

        assertEquals(Arrays.asList("initializer", "replacement-spawn"), events);
        assertSame(replacementController, lifecycle.controller());
    }

    @Test
    void shouldRunScheduledRepairOnNextHookWhenNoSchedulerIsAvailable() {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = createAttachedLifecycle();
        RecordingNativeEntity nativeEntity = new RecordingNativeEntity();
        List<String> events = new ArrayList<String>();

        lifecycle.scheduleNextTickRepair(new Runnable() {
            @Override
            public void run() {
                events.add("repair");
            }
        });

        lifecycle.onNativeHook("tick", nativeEntity, new Object[0]);

        assertEquals(Arrays.asList("repair"), events);
        assertEquals(1, nativeEntity.tickBaseInvocations);
    }

    @Test
    void shouldCreateAndTickSpawnedNetworkController() {
        List<String> events = new ArrayList<String>();
        EntityTemplate<Zombie> definition = EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "network"),
                        CustomEntityBaseType.ZOMBIE
                )
                .networkController(context -> new EntityNetworkController<Zombie>() {
                    @Override
                    public void onBind(@NotNull tech.guilhermekaua.spigotboot.entity.api.ControlledEntity<Zombie> entity,
                                       @NotNull EntityNetworkState state) {
                        events.add("bind");
                    }

                    @Override
                    public void onTick(@NotNull tech.guilhermekaua.spigotboot.entity.api.ControlledEntity<Zombie> entity,
                                       @NotNull EntityNetworkState state) {
                        events.add("tick:" + state.liveX() + ":" + state.liveVelocityX());
                    }
                })
                .build();

        RuntimeNativeEntityLifecycle<Zombie> lifecycle = new RuntimeNativeEntityLifecycle<Zombie>(
                definition,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)),
                MinecraftVersion.of(1, 21, 11)
        );
        lifecycle.bindHookBinder(new TestHookBinder<Zombie>());

        Zombie zombie = Mockito.mock(Zombie.class);
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getLocation()).thenReturn(new Location(Mockito.mock(World.class), 12.0D, 65.0D, -4.0D, 90.0F, 10.0F));
        when(zombie.getVelocity()).thenReturn(new Vector(1.5D, 0.0D, -0.5D));

        lifecycle.bind(zombie);
        lifecycle.onNativeHook("tick", new RecordingNativeEntity(), new Object[0]);

        assertEquals(Arrays.asList("bind", "tick:12.0:1.5"), events);
    }

    @Test
    void shouldPassRuntimeNativeLifecycleThroughPaperLikeFreshSpawnStrategy() {
        RecordingLifecycleAdapter adapter = RecordingLifecycleAdapter.paperLike();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        EntityTemplate<Zombie> definition = createSpawnSeamDefinition("paper-seam", adapter.paperSpawnEvents);

        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(
                definition,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D))
        );

        assertTrue(adapter.lastPaperSpawnLifecycle instanceof RuntimeNativeEntityLifecycle);
        assertNull(adapter.lastSpawnLifecycle);
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
                adapter.paperSpawnEvents
        );
        assertEquals("paper-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("paper-entry-and-state", platform.strategies().trackingBinding().id());
    }

    @Test
    void shouldPassRuntimeNativeLifecycleThroughLegacyFreshSpawnStrategy() {
        RecordingLifecycleAdapter adapter = RecordingLifecycleAdapter.legacy();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 8, 8), adapter);
        EntityTemplate<Zombie> definition = createSpawnSeamDefinition("legacy-seam", adapter.legacySpawnEvents);

        SpawnedEntity<Zombie> spawnedEntity = platform.spawn(
                definition,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D))
        );

        assertTrue(adapter.lastLegacySpawnLifecycle instanceof RuntimeNativeEntityLifecycle);
        assertNull(adapter.lastSpawnLifecycle);
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
                adapter.legacySpawnEvents
        );
        assertEquals("legacy-constructor-first", platform.strategies().freshSpawn().id());
        assertEquals("legacy-entry-only", platform.strategies().trackingBinding().id());
    }

    @Test
    void shouldPassRuntimeAttachedLifecycleThroughLegacyReplacementStrategy() {
        RecordingLifecycleAdapter adapter = RecordingLifecycleAdapter.legacy();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 8, 8), adapter);
        Zombie zombie = Mockito.mock(Zombie.class);

        when(zombie.isValid()).thenReturn(true);
        when(zombie.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);

        ControlledEntity<Zombie> attachedEntity = platform.get(zombie);

        assertTrue(adapter.lastAttachLifecycle instanceof RuntimeAttachedEntityLifecycle);
        assertEquals(0, adapter.paperReplacementInvocations);
        assertEquals(1, adapter.legacyReplacementInvocations);
        assertSame(adapter.lastTrackerEntryHandle, attachedEntity.networkState().trackerEntryHandle());
        assertNull(attachedEntity.networkState().trackerStateHandle());
        assertEquals("legacy-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("legacy-constructor-add-and-rewrite", platform.strategies().worldAdd().id());
    }

    @Test
    void shouldPassRuntimeAttachedLifecycleThroughPaperReplacementStrategy() {
        RecordingLifecycleAdapter adapter = RecordingLifecycleAdapter.paperLike();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        Zombie zombie = Mockito.mock(Zombie.class);

        when(zombie.isValid()).thenReturn(true);
        when(zombie.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);

        ControlledEntity<Zombie> attachedEntity = platform.get(zombie);

        assertTrue(adapter.lastAttachLifecycle instanceof RuntimeAttachedEntityLifecycle);
        assertEquals(1, adapter.paperReplacementInvocations);
        assertEquals(0, adapter.legacyReplacementInvocations);
        assertSame(adapter.lastTrackerEntryHandle, attachedEntity.networkState().trackerEntryHandle());
        assertSame(adapter.lastTrackerStateHandle, attachedEntity.networkState().trackerStateHandle());
        assertEquals("paper-reference-rewrite", platform.strategies().replacement().id());
        assertEquals("paper-entry-and-state", platform.strategies().trackingBinding().id());
    }

    private static BaseReplacementNativeHandle resolveSharedReplacementHandle(Entity entity) {
        return new BaseReplacementNativeHandle();
    }

    private static EntityTemplate<Zombie> createSpawnSeamDefinition(String id, List<String> events) {
        return EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", id),
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
                .controller(context -> new EntityController<Zombie>() {
                    @Override
                    public void onSpawn(@NotNull SpawnedEntity<Zombie> entity) {
                        events.add("spawn");
                    }
                })
                .build();
    }

    private static RuntimeAttachedEntityLifecycle<Zombie> createAttachedLifecycle() {
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 21, 11),
                new EntityController<Zombie>() {
                }
        );
        lifecycle.bindHookBinder(new TestHookBinder<Zombie>());

        Zombie zombie = Mockito.mock(Zombie.class);
        when(zombie.isValid()).thenReturn(true);
        lifecycle.bind(zombie);
        return lifecycle;
    }

    private static final class TestHookBinder<T extends Entity> implements NativeHookBinder<T> {
        @Override
        public @org.jetbrains.annotations.NotNull Collection<GeneratedNativeHookSpec> hookSpecs(
                @org.jetbrains.annotations.NotNull Class<?> nativeType
        ) {
            return java.util.Collections.emptyList();
        }

        @Override
        public Object dispatch(
                @org.jetbrains.annotations.NotNull AbstractRuntimeControlledEntity<T> controlledEntity,
                @org.jetbrains.annotations.NotNull LifecycleAwareNativeEntity nativeEntity,
                @org.jetbrains.annotations.NotNull String hookName,
                Object[] arguments
        ) {
            if ("tick".equals(hookName)) {
                controlledEntity.dispatchTick(new ContextualBaseInvoker<EntityTickContext<T>, Void>() {
                    @Override
                    public Void invoke(@org.jetbrains.annotations.NotNull EntityTickContext<T> context) {
                        nativeEntity.spigotBootInvokeBase("tick", new Object[0]);
                        return null;
                    }
                });
                return null;
            }
            if ("damage".equals(hookName)) {
                final float amount = ((Float) arguments[0]).floatValue();
                return Boolean.valueOf(controlledEntity.dispatchDamage(
                        amount,
                        new ContextualBaseInvoker<EntityDamageContext<T>, Boolean>() {
                            @Override
                            public Boolean invoke(@org.jetbrains.annotations.NotNull EntityDamageContext<T> context) {
                                return (Boolean) nativeEntity.spigotBootInvokeBase(
                                        "damage",
                                        new Object[]{Float.valueOf(context.amount())}
                                );
                            }
                        }
                ));
            }
            if ("remove".equals(hookName)) {
                controlledEntity.dispatchRemove(new ContextualBaseInvoker<EntityRemoveContext<T>, Void>() {
                    @Override
                    public Void invoke(@org.jetbrains.annotations.NotNull EntityRemoveContext<T> context) {
                        nativeEntity.spigotBootInvokeBase("remove", new Object[0]);
                        return null;
                    }
                });
                return null;
            }
            throw new IllegalArgumentException("Unexpected hook " + hookName);
        }
    }

    private static final class RecordingNativeEntity implements LifecycleAwareNativeEntity {
        private NativeEntityLifecycle<?> lifecycle;
        private int tickBaseInvocations;
        private int removeBaseInvocations;
        private float lastDamageAmount;

        @Override
        public void spigotBootBindLifecycle(NativeEntityLifecycle<?> lifecycle) {
            this.lifecycle = lifecycle;
        }

        @Override
        public NativeEntityLifecycle<?> spigotBootGetLifecycle() {
            return lifecycle;
        }

        @Override
        public Object spigotBootInvokeBase(String hookName, Object[] arguments) {
            if ("tick".equals(hookName)) {
                tickBaseInvocations++;
                return null;
            }
            if ("damage".equals(hookName)) {
                lastDamageAmount = ((Float) arguments[0]).floatValue();
                return Boolean.TRUE;
            }
            if ("remove".equals(hookName)) {
                removeBaseInvocations++;
                return null;
            }
            throw new IllegalArgumentException("Unexpected base hook " + hookName);
        }
    }

    private static final class RecordingLifecycleAdapter
            implements EntityVersionAdapter,
            EntityVersionMetadataProvider,
            PaperFreshSpawnStrategy_1_21_plus.Provider,
            LegacyFreshSpawnStrategy_1_8_to_1_12.Provider,
            PaperReplacementStrategy_1_21_plus.Provider,
            LegacyReplacementStrategy_1_8_to_1_12.Provider {
        private final MinecraftVersion version;
        private final EntityVersionCapabilities capabilities;
        private final EntityVersionBindings bindings;
        private final List<String> paperSpawnEvents = new ArrayList<String>();
        private final List<String> legacySpawnEvents = new ArrayList<String>();
        private NativeEntityLifecycle<?> lastSpawnLifecycle;
        private NativeEntityLifecycle<?> lastPaperSpawnLifecycle;
        private NativeEntityLifecycle<?> lastLegacySpawnLifecycle;
        private NativeEntityLifecycle<?> lastAttachLifecycle;
        private Entity lastReplacementBukkitEntity;
        private Object lastTrackerEntryHandle;
        private Object lastTrackerStateHandle;
        private int paperReplacementInvocations;
        private int legacyReplacementInvocations;

        private RecordingLifecycleAdapter(
                MinecraftVersion version,
                EntityVersionCapabilities capabilities,
                EntityVersionBindings bindings
        ) {
            this.version = version;
            this.capabilities = capabilities;
            this.bindings = bindings;
        }

        private static RecordingLifecycleAdapter paperLike() {
            return new RecordingLifecycleAdapter(
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
        }

        private static RecordingLifecycleAdapter legacy() {
            return new RecordingLifecycleAdapter(
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
            lastSpawnLifecycle = lifecycle;
            Zombie zombie = Mockito.mock(Zombie.class);
            when(zombie.isValid()).thenReturn(true);
            lifecycle.bind((T) zombie);
            lifecycle.onSpawn();
            return (SpawnedEntity<T>) lifecycle.handle();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            lastAttachLifecycle = lifecycle;
            RuntimeAttachedEntityLifecycle<T> attachedLifecycle = (RuntimeAttachedEntityLifecycle<T>) lifecycle;
            attachedLifecycle.bindHookBinder(new TestHookBinder<T>());
            attachedLifecycle.bind(entity);
            return attachedLifecycle.handle();
        }

        @Override
        public @NotNull PaperFreshSpawnStrategy_1_21_plus.Support paperFreshSpawnSupport() {
            return new PaperFreshSpawnStrategy_1_21_plus.Support() {
                @Override
                public <T extends Entity> PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn prepareFreshSpawn(
                        @NotNull EntityTemplate<T> template,
                        @NotNull SpawnOptions spawnOptions
                ) {
                    paperSpawnEvents.add("prepare");
                    lastTrackerEntryHandle = null;
                    lastTrackerStateHandle = null;
                    Zombie zombie = Mockito.mock(Zombie.class);
                    when(zombie.isValid()).thenReturn(true);
                    return new PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn(zombie);
                }

                @Override
                public @NotNull Object createNativeEntity(
                        @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                        @NotNull Location location
                ) {
                    paperSpawnEvents.add("create-native");
                    return preparedSpawn.preparedMetadata();
                }

                @Override
                public <T extends Entity> void bindLifecycleToNativeEntity(
                        @NotNull Object nativeEntity,
                        @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
                        @NotNull NativeEntityLifecycle<T> lifecycle
                ) {
                    paperSpawnEvents.add("bind-native");
                    lastPaperSpawnLifecycle = lifecycle;
                }

                @Override
                public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
                    paperSpawnEvents.add("resolve-bukkit");
                    return (Entity) nativeEntity;
                }

                @Override
                public void beforeWorldAdd(@NotNull Object nativeEntity, @NotNull Location location) {
                    paperSpawnEvents.add("add-world");
                }

                @Override
                public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
                    return new PaperFreshWorldHandle();
                }

                @Override
                public @NotNull PaperFreshSpawnStrategy_1_21_plus.TrackingHandles resolveTrackingHandles(
                        @NotNull Object nativeEntity
                ) {
                    paperSpawnEvents.add("tracking");
                    lastTrackerEntryHandle = new Object();
                    lastTrackerStateHandle = new Object();
                    return new PaperFreshSpawnStrategy_1_21_plus.TrackingHandles(
                            lastTrackerEntryHandle,
                            lastTrackerStateHandle
                    );
                }
            };
        }

        @Override
        public @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.Support legacyFreshSpawnSupport() {
            return new LegacyFreshSpawnStrategy_1_8_to_1_12.Support() {
                @Override
                public <T extends Entity> LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn prepareFreshSpawn(
                        @NotNull EntityTemplate<T> template,
                        @NotNull SpawnOptions spawnOptions
                ) {
                    legacySpawnEvents.add("prepare");
                    lastTrackerEntryHandle = null;
                    lastTrackerStateHandle = null;
                    Zombie zombie = Mockito.mock(Zombie.class);
                    when(zombie.isValid()).thenReturn(true);
                    return new LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn(zombie);
                }

                @Override
                public @NotNull Object createNativeEntity(
                        @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
                        @NotNull Location location
                ) {
                    legacySpawnEvents.add("create-native");
                    return preparedSpawn.preparedMetadata();
                }

                @Override
                public <T extends Entity> void bindLifecycleToNativeEntity(
                        @NotNull Object nativeEntity,
                        @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12.PreparedSpawn preparedSpawn,
                        @NotNull NativeEntityLifecycle<T> lifecycle
                ) {
                    legacySpawnEvents.add("bind-native");
                    lastLegacySpawnLifecycle = lifecycle;
                }

                @Override
                public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
                    legacySpawnEvents.add("resolve-bukkit");
                    return (Entity) nativeEntity;
                }

                @Override
                public void beforeWorldAdd(@NotNull Object nativeEntity, @NotNull Location location) {
                    legacySpawnEvents.add("add-world");
                }

                @Override
                public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
                    return new LegacyFreshWorldHandle();
                }

                @Override
                public Object resolveTrackerEntryHandle(@NotNull Object nativeEntity) {
                    legacySpawnEvents.add("tracking");
                    lastTrackerEntryHandle = new Object();
                    lastTrackerStateHandle = null;
                    return lastTrackerEntryHandle;
                }
            };
        }

        @Override
        public @NotNull PaperReplacementStrategy_1_21_plus.Support paperReplacementSupport() {
            return new PaperReplacementStrategy_1_21_plus.Support() {
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
                    paperReplacementInvocations++;
                    lastReplacementBukkitEntity = entity;
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
                    lastAttachLifecycle = lifecycle;
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
                public @NotNull PaperReplacementStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
                        @NotNull Object replacementHandle
                ) {
                    lastTrackerEntryHandle = new Object();
                    lastTrackerStateHandle = new Object();
                    return new PaperReplacementStrategy_1_21_plus.TrackingHandles(
                            lastTrackerEntryHandle,
                            lastTrackerStateHandle
                    );
                }

                @Override
                public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
                    return lastReplacementBukkitEntity;
                }

                @Override
                public <T extends Entity> void scheduleRepairPass(
                        @NotNull NativeEntityLifecycle<T> lifecycle,
                        @NotNull T entity,
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                }
            };
        }

        @Override
        public @NotNull LegacyReplacementStrategy_1_8_to_1_12.Support legacyReplacementSupport() {
            return new LegacyReplacementStrategy_1_8_to_1_12.Support() {
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
                    legacyReplacementInvocations++;
                    lastReplacementBukkitEntity = entity;
                    return new LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement(currentNativeHandle);
                }

                @Override
                public @NotNull Object allocateReplacementHandle(
                        @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
                ) {
                    return new GeneratedReplacementNativeHandle();
                }

                @Override
                public <T extends Entity> void bindLifecycleToReplacement(
                        @NotNull Object replacementHandle,
                        @NotNull LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement,
                        @NotNull NativeEntityLifecycle<T> lifecycle
                ) {
                    lastAttachLifecycle = lifecycle;
                }

                @Override
                public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
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
                    return lastReplacementBukkitEntity;
                }

                @Override
                public Object resolveTrackerEntryHandle(@NotNull Object replacementHandle) {
                    lastTrackerEntryHandle = new Object();
                    lastTrackerStateHandle = null;
                    return lastTrackerEntryHandle;
                }

                @Override
                public <T extends Entity> void scheduleRepairPass(
                        @NotNull NativeEntityLifecycle<T> lifecycle,
                        @NotNull T entity,
                        @NotNull Object currentNativeHandle,
                        @NotNull Object replacementHandle
                ) {
                }
            };
        }
    }

    private static class BaseReplacementNativeHandle {
        @SuppressWarnings("unused")
        private Object marker = new Object();
    }

    private static final class GeneratedReplacementNativeHandle extends BaseReplacementNativeHandle {
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
}
