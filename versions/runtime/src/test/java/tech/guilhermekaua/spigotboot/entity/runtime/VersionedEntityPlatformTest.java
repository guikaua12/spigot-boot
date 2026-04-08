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
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class VersionedEntityPlatformTest {

    @Test
    void shouldRegisterDefinitionsAndLookupById() {
        VersionedEntityPlatform platform = new VersionedEntityPlatform(
                MinecraftVersion.of(1, 21, 11),
                new RecordingAdapter()
        );
        CustomEntityDefinition<Zombie> definition = createDefinition(new ArrayList<String>());

        platform.registerDefinition(definition);

        assertSame(definition, platform.definition(definition.id()));
        assertEquals(1, platform.definitions().size());
    }

    @Test
    void shouldSpawnRegisteredDefinitionsThroughSharedLifecycle() {
        RecordingAdapter adapter = new RecordingAdapter();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        CustomEntityDefinition<Zombie> definition = createDefinition(new ArrayList<String>());
        CustomEntitySpawnRequest spawnRequest = CustomEntitySpawnRequest.builder(
                new Location(Mockito.mock(World.class), 10.0D, 64.0D, 12.0D)
        ).build();

        platform.registerDefinition(definition);
        CustomEntityHandle<Zombie> handle = platform.spawn(definition, spawnRequest);

        assertEquals(definition.id(), handle.definitionId());
        assertEquals(MinecraftVersion.of(1, 21, 11), handle.minecraftVersion());
        assertSame(handle, adapter.lastSpawnHandle);
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
        adapter.attachedEntity = controlledEntity;

        ControlledEntity<Entity> first = platform.entity(entity);
        ControlledEntity<Entity> second = platform.entity(entity);

        assertSame(controlledEntity, first);
        assertSame(first, second);
        assertEquals(1, adapter.attachInvocations);
    }

    @Test
    void shouldRequireControllerFactoryWhenBuildingDefinition() {
        CustomEntityDefinition.Builder<Zombie> builder =
                CustomEntityDefinition.builder(
                        CustomEntityId.of("test", "missing-controller"),
                        CustomEntityBaseType.ZOMBIE
                );

        IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);

        assertEquals("controllerFactory cannot be null", exception.getMessage());
    }

    private static CustomEntityDefinition<Zombie> createDefinition(List<String> events) {
        return CustomEntityDefinition.<Zombie>builder(
                        CustomEntityId.of("test", "orbit"),
                        CustomEntityBaseType.ZOMBIE
                )
                .initializer(context -> events.add("initializer"))
                .controllerFactory(context -> new tech.guilhermekaua.spigotboot.entity.api.EntityController<Zombie>() {
                    @Override
                    public void onSpawn(tech.guilhermekaua.spigotboot.entity.api.@NotNull CustomEntityContext<Zombie> context) {
                        events.add("spawn");
                    }
                })
                .build();
    }

    private static final class RecordingAdapter implements EntityVersionAdapter {
        private CustomEntityHandle<Zombie> lastSpawnHandle;
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
        public <T extends Entity> CustomEntityHandle<T> spawn(
                CustomEntityDefinition<T> definition,
                CustomEntitySpawnRequest spawnRequest,
                NativeEntityLifecycle<T> lifecycle
        ) {
            HandleAwareZombie zombie = Mockito.mock(HandleAwareZombie.class);
            when(zombie.isValid()).thenReturn(true);
            when(zombie.getHandle()).thenReturn(new Object());

            lifecycle.bind((T) zombie);
            lifecycle.onSpawn();

            lastSpawnHandle = (CustomEntityHandle<Zombie>) lifecycle.handle();
            return (CustomEntityHandle<T>) lifecycle.handle();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Entity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            attachInvocations++;
            return (ControlledEntity<T>) attachedEntity;
        }
    }

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }

    private interface HandleAwareEntity extends Entity {
        Object getHandle();
    }
}
