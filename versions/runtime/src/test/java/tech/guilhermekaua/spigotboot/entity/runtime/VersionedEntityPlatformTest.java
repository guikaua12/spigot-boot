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
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        Object nativeHandle = new Object();
        @SuppressWarnings("unchecked")
        ControlledEntity<Entity> controlledEntity = Mockito.mock(ControlledEntity.class);

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
        assertEquals(1, adapter.attachInvocations);
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

        assertNotSame(first, second);
        assertEquals(2, adapter.attachInvocations);
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

    private static final class RecordingAdapter implements EntityVersionAdapter {
        private SpawnedEntity<Zombie> lastSpawnHandle;
        private EntityTemplate<?> lastSpawnTemplate;
        private SpawnOptions lastSpawnOptions;
        private ControlledEntity<?> attachedEntity;
        private int attachInvocations;

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
            HandleAwareZombie zombie = Mockito.mock(HandleAwareZombie.class);
            when(zombie.isValid()).thenReturn(true);
            when(zombie.getHandle()).thenReturn(new Object());

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

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }

    private interface HandleAwareEntity extends Entity {
        Object getHandle();
    }
}
