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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityNetworkControllerTest {

    @Test
    void passThroughFactoryShouldReturnTheSharedInstance() {
        assertSame(EntityNetworkController.passThrough(), SpawnNetworkControllerFactory.passThrough().create(new DummySpawnContext()));
    }

    @Test
    void shouldApplyRelativeAbsoluteRotationAndVelocityThresholds() {
        EntityNetworkController<Entity> controller = EntityNetworkController.passThrough();
        EntityNetworkState state = new EntityNetworkState();

        state.setLivePosition(0.04D, 0.0D, 0.0D);
        assertTrue(controller.shouldSendRelativeSync(state));

        state.setSyncedPosition(0.0D, 0.0D, 0.0D);
        state.setLivePosition(9.0D, 0.0D, 0.0D);
        assertTrue(controller.shouldSendAbsoluteSync(state));

        state.setLivePosition(0.0D, 0.0D, 0.0D);
        state.setTicksSinceAbsoluteSync(EntityNetworkController.ABSOLUTE_RESYNC_INTERVAL);
        assertTrue(controller.shouldSendAbsoluteSync(state));

        state.resetAbsoluteSyncCounter();
        state.setLiveRotation(5.0F, 0.0F);
        assertTrue(controller.shouldSendRotationSync(state));

        state.setLiveRotation(0.0F, 0.0F);
        state.setLiveHeadYaw(5.0F);
        assertTrue(controller.shouldSendRotationSync(state));

        state.setLiveHeadYaw(0.0F);
        state.setLiveVelocity(0.03D, 0.0D, 0.0D);
        assertTrue(controller.shouldSendVelocitySync(state));

        state.markPositionSyncedFromLive();
        state.markVelocitySyncedFromLive();
        state.markRotationSyncedFromLive();
        state.markHeadRotationSyncedFromLive();

        assertFalse(controller.shouldSendRelativeSync(state));
        assertFalse(controller.shouldSendRotationSync(state));
        assertFalse(controller.shouldSendVelocitySync(state));
    }

    private static final class DummySpawnContext implements SpawnContext<Entity> {
        private final EntityTemplate<Entity> template = EntityTemplate.builder(CustomEntityBaseType.ZOMBIE).build();

        @Override
        public EntityTemplate<Entity> template() {
            return template;
        }

        @Override
        public CustomEntityBaseType baseType() {
            return CustomEntityBaseType.ZOMBIE;
        }

        @Override
        public Class<Entity> bukkitType() {
            return Entity.class;
        }

        @Override
        public MinecraftVersion minecraftVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public SpawnOptions spawnOptions() {
            return SpawnOptions.at(new org.bukkit.Location(org.mockito.Mockito.mock(org.bukkit.World.class), 0.0D, 0.0D, 0.0D));
        }
    }
}
