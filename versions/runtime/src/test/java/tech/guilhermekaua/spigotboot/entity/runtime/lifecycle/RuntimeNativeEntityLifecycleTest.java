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
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.ZombieEntityDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeNativeEntityLifecycleTest {

    @Test
    void shouldInvokeRemoveWhenHandleRequestsRemoval() {
        List<String> events = new ArrayList<String>();
        ZombieEntityDefinition definition = ZombieEntityDefinition.builder(CustomEntityId.of("test", "remove"))
                .behaviorFactory(context -> new tech.guilhermekaua.spigotboot.entity.api.CustomEntityBehavior<Zombie>() {
                    @Override
                    public void onRemove(tech.guilhermekaua.spigotboot.entity.api.CustomEntityContext<Zombie> context) {
                        events.add("remove");
                    }
                })
                .build();

        RuntimeNativeEntityLifecycle<Zombie> lifecycle = new RuntimeNativeEntityLifecycle<Zombie>(
                definition,
                CustomEntitySpawnRequest.builder(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)).build(),
                MinecraftVersion.of(1, 8, 8)
        );

        Zombie zombie = Mockito.mock(Zombie.class);
        when(zombie.isValid()).thenReturn(true);
        when(zombie.isDead()).thenReturn(false);

        lifecycle.bind(zombie);
        lifecycle.onSpawn();
        lifecycle.remove();

        verify(zombie).remove();
        assertEquals("remove", String.join(",", events));
    }
}
