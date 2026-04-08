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
package tech.guilhermekaua.spigotboot.entity.runtime.nativebridge;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityState;
import tech.guilhermekaua.spigotboot.entity.api.EntityController;
import tech.guilhermekaua.spigotboot.entity.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.RuntimeAttachedEntityLifecycle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GeneratedNativeEntityClassFactoryTest {

    @Test
    void shouldGenerateAndRouteAllConfiguredHookCategories() throws Exception {
        GeneratedNativeEntityClassFactory factory = new GeneratedNativeEntityClassFactory();
        List<GeneratedNativeHookSpec> hookSpecs = Arrays.asList(
                GeneratedNativeHookSpec.of("tick", StubNativeEntity.class.getDeclaredMethod("tick")),
                GeneratedNativeHookSpec.of("move", StubNativeEntity.class.getDeclaredMethod("move", double.class, double.class, double.class)),
                GeneratedNativeHookSpec.of("push", StubNativeEntity.class.getDeclaredMethod("push", double.class, double.class, double.class)),
                GeneratedNativeHookSpec.of("damage", StubNativeEntity.class.getDeclaredMethod("damage", float.class)),
                GeneratedNativeHookSpec.of("interact", StubNativeEntity.class.getDeclaredMethod("interact", StubPlayer.class, StubHand.class)),
                GeneratedNativeHookSpec.of("die", StubNativeEntity.class.getDeclaredMethod("die")),
                GeneratedNativeHookSpec.of("remove", StubNativeEntity.class.getDeclaredMethod("remove")),
                GeneratedNativeHookSpec.of("collide", StubNativeEntity.class.getDeclaredMethod("collide", StubOtherEntity.class)),
                GeneratedNativeHookSpec.of("positionPassenger", StubNativeEntity.class.getDeclaredMethod("positionPassenger", StubOtherEntity.class)),
                GeneratedNativeHookSpec.of(
                        "inventoryChange",
                        StubNativeEntity.class.getDeclaredMethod("inventoryChange", StubSlot.class, StubItem.class, StubItem.class)
                )
        );

        Class<?> generatedType = factory.createSubclass(
                StubNativeEntity.class,
                "tech.guilhermekaua.spigotboot.entity.generated.test.StubNativeEntityProxy",
                hookSpecs
        );

        Object generatedEntity = generatedType.getDeclaredConstructor().newInstance();
        factory.installInterceptor(generatedEntity, hookSpecs);

        RecordingLifecycle lifecycle = new RecordingLifecycle();
        factory.bindLifecycle(generatedEntity, lifecycle);

        LifecycleAwareNativeEntity awareEntity = (LifecycleAwareNativeEntity) generatedEntity;
        assertSame(lifecycle, awareEntity.spigotBootGetLifecycle());

        StubNativeEntity nativeEntity = (StubNativeEntity) generatedEntity;
        nativeEntity.tick();
        nativeEntity.move(1.0D, 2.0D, 3.0D);
        nativeEntity.push(4.0D, 5.0D, 6.0D);
        assertTrue(nativeEntity.damage(7.0F));
        assertSame(StubInteractionResult.SUCCESS, nativeEntity.interact(new StubPlayer(), StubHand.MAIN));
        nativeEntity.die();
        nativeEntity.remove();
        nativeEntity.collide(new StubOtherEntity());
        nativeEntity.positionPassenger(new StubOtherEntity());
        nativeEntity.inventoryChange(StubSlot.HEAD, new StubItem("old"), new StubItem("new"));

        assertEquals(
                Arrays.asList(
                        "tick",
                        "move",
                        "push",
                        "damage",
                        "interact",
                        "die",
                        "remove",
                        "collide",
                        "positionPassenger",
                        "inventoryChange"
                ),
                lifecycle.hookNames
        );

        awareEntity.spigotBootInvokeBase("tick", new Object[0]);
        awareEntity.spigotBootInvokeBase("move", new Object[]{Double.valueOf(9.0D), Double.valueOf(8.0D), Double.valueOf(7.0D)});
        awareEntity.spigotBootInvokeBase("push", new Object[]{Double.valueOf(6.0D), Double.valueOf(5.0D), Double.valueOf(4.0D)});
        awareEntity.spigotBootInvokeBase("damage", new Object[]{Float.valueOf(3.0F)});
        awareEntity.spigotBootInvokeBase("interact", new Object[]{new StubPlayer(), StubHand.OFF});
        awareEntity.spigotBootInvokeBase("die", new Object[0]);
        awareEntity.spigotBootInvokeBase("remove", new Object[0]);
        awareEntity.spigotBootInvokeBase("collide", new Object[]{new StubOtherEntity()});
        awareEntity.spigotBootInvokeBase("positionPassenger", new Object[]{new StubOtherEntity()});
        awareEntity.spigotBootInvokeBase(
                "inventoryChange",
                new Object[]{StubSlot.CHEST, new StubItem("before"), new StubItem("after")}
        );

        assertEquals(1, nativeEntity.baseTickInvocations);
        assertEquals(1, nativeEntity.baseMoveInvocations);
        assertEquals(1, nativeEntity.basePushInvocations);
        assertEquals(1, nativeEntity.baseDamageInvocations);
        assertEquals(1, nativeEntity.baseInteractInvocations);
        assertEquals(1, nativeEntity.baseDieInvocations);
        assertEquals(1, nativeEntity.baseRemoveInvocations);
        assertEquals(1, nativeEntity.baseCollideInvocations);
        assertEquals(1, nativeEntity.basePositionPassengerInvocations);
        assertEquals(1, nativeEntity.baseInventoryChangeInvocations);
    }

    @Test
    void shouldAllowNestedRemoveOverloadsToReachBaseWhileDispatchingControllerOnce() throws Exception {
        GeneratedNativeEntityClassFactory factory = new GeneratedNativeEntityClassFactory();
        List<GeneratedNativeHookSpec> hookSpecs = Arrays.asList(
                GeneratedNativeHookSpec.of(
                        "remove",
                        StubPatchedRemoveNativeEntity.class.getDeclaredMethod("remove", StubRemovalReason.class)
                ),
                GeneratedNativeHookSpec.of(
                        "removeWithCause",
                        StubPatchedRemoveNativeEntity.class.getDeclaredMethod(
                                "remove",
                                StubRemovalReason.class,
                                StubRemoveCause.class
                        )
                )
        );

        Class<?> generatedType = factory.createSubclass(
                StubPatchedRemoveNativeEntity.class,
                "tech.guilhermekaua.spigotboot.entity.generated.test.StubPatchedRemoveNativeEntityProxy",
                hookSpecs
        );

        Object generatedEntity = generatedType.getDeclaredConstructor().newInstance();
        factory.installInterceptor(generatedEntity, hookSpecs);

        Entity bukkitEntity = Mockito.mock(Entity.class);
        when(bukkitEntity.isValid()).thenReturn(true);

        int[] removeHookInvocations = new int[1];
        RuntimeAttachedEntityLifecycle<Entity> lifecycle = new RuntimeAttachedEntityLifecycle<Entity>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 21, 11),
                new EntityController<Entity>() {
                    @Override
                    public void onRemove(EntityRemoveContext<Entity> context) {
                        removeHookInvocations[0]++;
                        context.base().invoke();
                    }
                }
        );
        lifecycle.bindHookBinder(new RemoveAwareHookBinder());
        lifecycle.bind(bukkitEntity);
        factory.bindLifecycle(generatedEntity, lifecycle);

        StubPatchedRemoveNativeEntity nativeEntity = (StubPatchedRemoveNativeEntity) generatedEntity;
        nativeEntity.remove(StubRemovalReason.KILLED);

        assertEquals(1, removeHookInvocations[0]);
        assertEquals(1, nativeEntity.baseRemoveDelegatingInvocations);
        assertEquals(1, nativeEntity.baseRemoveWithCauseInvocations);
        assertTrue(lifecycle.isRemoved());
    }

    public static class StubNativeEntity {
        private int baseTickInvocations;
        private int baseMoveInvocations;
        private int basePushInvocations;
        private int baseDamageInvocations;
        private int baseInteractInvocations;
        private int baseDieInvocations;
        private int baseRemoveInvocations;
        private int baseCollideInvocations;
        private int basePositionPassengerInvocations;
        private int baseInventoryChangeInvocations;

        public void tick() {
            baseTickInvocations++;
        }

        public void move(double x, double y, double z) {
            baseMoveInvocations++;
        }

        public void push(double x, double y, double z) {
            basePushInvocations++;
        }

        public boolean damage(float amount) {
            baseDamageInvocations++;
            return true;
        }

        public StubInteractionResult interact(StubPlayer player, StubHand hand) {
            baseInteractInvocations++;
            return StubInteractionResult.SUCCESS;
        }

        public void die() {
            baseDieInvocations++;
        }

        public void remove() {
            baseRemoveInvocations++;
        }

        public void collide(StubOtherEntity entity) {
            baseCollideInvocations++;
        }

        public void positionPassenger(StubOtherEntity entity) {
            basePositionPassengerInvocations++;
        }

        public void inventoryChange(StubSlot slot, StubItem previousItem, StubItem newItem) {
            baseInventoryChangeInvocations++;
        }
    }

    public static class StubPatchedRemoveNativeEntity {
        private int baseRemoveDelegatingInvocations;
        private int baseRemoveWithCauseInvocations;

        public void remove(StubRemovalReason reason) {
            baseRemoveDelegatingInvocations++;
            this.remove(reason, null);
        }

        public void remove(StubRemovalReason reason, StubRemoveCause cause) {
            baseRemoveWithCauseInvocations++;
        }
    }

    private static final class RecordingLifecycle implements NativeEntityLifecycle<Entity> {
        private final List<String> hookNames = new ArrayList<String>();

        @Override
        public void bind(Entity bukkitEntity) {
        }

        @Override
        public void onSpawn() {
        }

        @Override
        public Object onNativeHook(String hookName, LifecycleAwareNativeEntity nativeEntity, Object[] arguments) {
            hookNames.add(hookName);
            if ("damage".equals(hookName)) {
                return Boolean.TRUE;
            }
            if ("interact".equals(hookName)) {
                return StubInteractionResult.SUCCESS;
            }
            return null;
        }

        @Override
        public ControlledEntity<Entity> handle() {
            return new ControlledEntity<Entity>() {
                @Override
                public Entity bukkitEntity() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CustomEntityState state() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean isRemoved() {
                    return false;
                }

                @Override
                public CustomEntityBaseType baseType() {
                    return CustomEntityBaseType.ZOMBIE;
                }

                @Override
                public MinecraftVersion minecraftVersion() {
                    return MinecraftVersion.of(1, 21, 11);
                }

                @Override
                public EntityController<Entity> controller() {
                    return new EntityController<Entity>() {
                    };
                }

                @Override
                public void setController(EntityController<Entity> controller) {
                }

                @Override
                public void clearController() {
                }

                @Override
                public boolean isHooked() {
                    return true;
                }
            };
        }
    }

    private static final class RemoveAwareHookBinder implements NativeHookBinder<Entity> {
        @Override
        public Collection<GeneratedNativeHookSpec> hookSpecs(Class<?> nativeType) {
            return java.util.Collections.emptyList();
        }

        @Override
        public Object dispatch(
                AbstractRuntimeControlledEntity<Entity> controlledEntity,
                LifecycleAwareNativeEntity nativeEntity,
                String hookName,
                Object[] arguments
        ) {
            if (!"remove".equals(hookName) && !"removeWithCause".equals(hookName)) {
                throw new IllegalArgumentException("Unexpected hook " + hookName);
            }

            final Object[] baseArguments = arguments != null ? arguments : new Object[0];
            controlledEntity.dispatchRemove(new ContextualBaseInvoker<EntityRemoveContext<Entity>, Void>() {
                @Override
                public Void invoke(EntityRemoveContext<Entity> context) {
                    nativeEntity.spigotBootInvokeBase(hookName, baseArguments);
                    return null;
                }
            });
            return null;
        }
    }

    public static final class StubPlayer {
    }

    public static final class StubOtherEntity {
    }

    public enum StubHand {
        MAIN,
        OFF
    }

    public enum StubRemovalReason {
        KILLED
    }

    public enum StubRemoveCause {
        DEATH
    }

    public enum StubInteractionResult {
        SUCCESS,
        FAIL
    }

    public enum StubSlot {
        HEAD,
        CHEST
    }

    public static final class StubItem {
        private final String name;

        public StubItem(String name) {
            this.name = name;
        }
    }
}
