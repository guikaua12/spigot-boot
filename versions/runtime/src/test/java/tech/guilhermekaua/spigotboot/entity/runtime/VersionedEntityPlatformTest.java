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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.ControlledZombie;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.ZombieEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

class VersionedEntityPlatformTest {

    @Test
    void shouldRegisterDefinitionsAndLookupById() {
        VersionedEntityPlatform platform = new VersionedEntityPlatform(
                MinecraftVersion.of(1, 21, 11),
                new RecordingAdapter()
        );
        ZombieEntityDefinition definition = createDefinition(new ArrayList<String>());

        platform.registerDefinition(definition);

        assertSame(definition, platform.definition(definition.id()));
        assertEquals(1, platform.definitions().size());
    }

    @Test
    void shouldSpawnRegisteredDefinitionsThroughSharedLifecycle() {
        RecordingAdapter adapter = new RecordingAdapter();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        ZombieEntityDefinition definition = createDefinition(new ArrayList<String>());
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
    void shouldCacheAttachedZombiesByBukkitIdentity() {
        RecordingAdapter adapter = new RecordingAdapter();
        VersionedEntityPlatform platform = new VersionedEntityPlatform(MinecraftVersion.of(1, 21, 11), adapter);
        HandleAwareZombie zombie = Mockito.mock(HandleAwareZombie.class);
        Object nativeHandle = new Object();
        ControlledZombie controlledZombie = Mockito.mock(ControlledZombie.class);

        when(zombie.getHandle()).thenReturn(nativeHandle);
        when(controlledZombie.bukkitEntity()).thenReturn(zombie);
        adapter.attachedZombie = controlledZombie;

        ControlledZombie first = platform.zombie(zombie);
        ControlledZombie second = platform.zombie(zombie);

        assertSame(controlledZombie, first);
        assertSame(first, second);
        assertEquals(1, adapter.attachInvocations);
    }

    private static ZombieEntityDefinition createDefinition(List<String> events) {
        return ZombieEntityDefinition.builder(CustomEntityId.of("test", "orbit"))
                .initializer(context -> events.add("initializer"))
                .behaviorFactory(context -> new tech.guilhermekaua.spigotboot.entity.api.CustomEntityBehavior<Zombie>() {
                    @Override
                    public void onSpawn(tech.guilhermekaua.spigotboot.entity.api.CustomEntityContext<Zombie> context) {
                        events.add("spawn");
                    }
                })
                .build();
    }

    private static final class RecordingAdapter implements EntityVersionAdapter {
        private CustomEntityHandle<Zombie> lastSpawnHandle;
        private ControlledZombie attachedZombie;
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
        public <T extends LivingEntity> CustomEntityHandle<T> spawn(
                CustomEntityDefinition<T> definition,
                CustomEntitySpawnRequest spawnRequest,
                NativeEntityLifecycle<T> lifecycle
        ) {
            HandleAwareZombie zombie = Mockito.mock(HandleAwareZombie.class);
            when(zombie.isValid()).thenReturn(true);
            when(zombie.isDead()).thenReturn(false);
            when(zombie.getHandle()).thenReturn(new Object());

            lifecycle.bind((T) zombie);
            lifecycle.onSpawn();

            lastSpawnHandle = (CustomEntityHandle<Zombie>) lifecycle.handle();
            return (CustomEntityHandle<T>) lifecycle.handle();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends LivingEntity> ControlledEntity<T> attach(T entity, NativeEntityLifecycle<T> lifecycle) {
            attachInvocations++;
            return (ControlledEntity<T>) attachedZombie;
        }
    }

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }
}
