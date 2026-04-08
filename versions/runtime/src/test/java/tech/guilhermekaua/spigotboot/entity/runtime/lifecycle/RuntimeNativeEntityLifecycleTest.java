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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.EntityController;
import tech.guilhermekaua.spigotboot.entity.api.EntityDamageContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        CustomEntityDefinition<Zombie> definition = CustomEntityDefinition.<Zombie>builder(
                        CustomEntityId.of("test", "behavior"),
                        CustomEntityBaseType.ZOMBIE
                )
                .initializer(context -> events.add("initializer"))
                .controllerFactory(context -> new EntityController<Zombie>() {
                    @Override
                    public void onSpawn(tech.guilhermekaua.spigotboot.entity.api.@NotNull CustomEntityContext<Zombie> context) {
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
                CustomEntitySpawnRequest.builder(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)).build(),
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
            public void onSpawn(@NotNull tech.guilhermekaua.spigotboot.entity.api.CustomEntityContext<Zombie> context) {
                events.add("replacement-spawn");
            }
        };

        CustomEntityDefinition<Zombie> definition = CustomEntityDefinition.<Zombie>builder(
                        CustomEntityId.of("test", "spawn-controller-swap"),
                        CustomEntityBaseType.ZOMBIE
                )
                .initializer(context -> {
                    events.add("initializer");
                    context.setController(replacementController);
                })
                .controllerFactory(context -> new EntityController<Zombie>() {
                    @Override
                    public void onSpawn(@NotNull tech.guilhermekaua.spigotboot.entity.api.CustomEntityContext<Zombie> context) {
                        events.add("factory-spawn");
                    }
                })
                .build();

        RuntimeNativeEntityLifecycle<Zombie> lifecycle = new RuntimeNativeEntityLifecycle<Zombie>(
                definition,
                CustomEntitySpawnRequest.builder(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)).build(),
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
}
