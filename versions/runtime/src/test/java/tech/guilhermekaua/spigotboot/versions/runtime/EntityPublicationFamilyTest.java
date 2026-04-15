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
package tech.guilhermekaua.spigotboot.versions.runtime;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.controller.PassThroughEntityController;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntitiesByUuidPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationFreshSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationReplacementSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.LegacyWorldListenerPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.NoOpEntityPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.PaperChunkSystemPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.PaperMoonriseChunkSystemPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.SectionManagerPublicationBackend;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperReplacementStrategy_1_21_plus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class EntityPublicationFamilyTest {

    @Test
    void shouldResolveExplicitBackendForEveryPublicationFamily() {
        assertInstanceOf(
                NoOpEntityPublicationBackend.class,
                EntityPublicationBackendResolver.resolve(EntityPublicationFamily.UNSPECIFIED)
        );
        assertInstanceOf(
                LegacyWorldListenerPublicationBackend.class,
                EntityPublicationBackendResolver.resolve(EntityPublicationFamily.LEGACY_WORLD_LISTENER)
        );
        assertInstanceOf(
                EntitiesByUuidPublicationBackend.class,
                EntityPublicationBackendResolver.resolve(EntityPublicationFamily.ENTITIES_BY_UUID)
        );
        assertInstanceOf(
                SectionManagerPublicationBackend.class,
                EntityPublicationBackendResolver.resolve(EntityPublicationFamily.SECTION_MANAGER)
        );
        assertInstanceOf(
                PaperChunkSystemPublicationBackend.class,
                EntityPublicationBackendResolver.resolve(EntityPublicationFamily.PAPER_CHUNK_SYSTEM)
        );
        assertInstanceOf(
                PaperMoonriseChunkSystemPublicationBackend.class,
                EntityPublicationBackendResolver.resolve(EntityPublicationFamily.PAPER_MOONRISE_CHUNK_SYSTEM)
        );
    }

    @Test
    void shouldUseLegacyAddEntityMethodDuringFreshPublication() {
        LegacyLevelHandle levelHandle = new LegacyLevelHandle();
        RecordingFreshSupport support = new RecordingFreshSupport(levelHandle);
        World world = Mockito.mock(World.class);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(Mockito.mock(Chunk.class));

        EntityPublicationBackendResolver.resolve(EntityPublicationFamily.LEGACY_WORLD_LISTENER).addFreshEntity(
                support,
                new Object(),
                new Location(world, 32.0D, 65.0D, 48.0D)
        );

        assertEquals(1, support.beforeWorldAddInvocations);
        assertEquals(1, levelHandle.addEntityInvocations);
        assertEquals(0, levelHandle.addFreshEntityInvocations);
    }

    @Test
    void shouldPreferModernFreshAddMethodDuringFreshPublication() {
        ModernLevelHandle levelHandle = new ModernLevelHandle();
        RecordingFreshSupport support = new RecordingFreshSupport(levelHandle);
        World world = Mockito.mock(World.class);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(Mockito.mock(Chunk.class));

        EntityPublicationBackendResolver.resolve(EntityPublicationFamily.PAPER_CHUNK_SYSTEM).addFreshEntity(
                support,
                new Object(),
                new Location(world, 0.0D, 70.0D, 0.0D)
        );

        assertEquals(1, support.beforeWorldAddInvocations);
        assertEquals(1, levelHandle.addFreshEntityInvocations);
        assertEquals(0, levelHandle.addWithUuidInvocations);
        assertEquals(0, levelHandle.addEntityInvocations);
    }

    @Test
    void shouldFallbackToCustomSpawnReasonOverloadWhenModernWorldAddRequiresIt() {
        ReasonedModernLevelHandle levelHandle = new ReasonedModernLevelHandle();
        RecordingFreshSupport support = new RecordingFreshSupport(levelHandle);
        World world = Mockito.mock(World.class);
        when(world.getChunkAt(anyInt(), anyInt())).thenReturn(Mockito.mock(Chunk.class));

        EntityPublicationBackendResolver.resolve(EntityPublicationFamily.PAPER_CHUNK_SYSTEM).addFreshEntity(
                support,
                new Object(),
                new Location(world, 0.0D, 70.0D, 0.0D)
        );

        assertEquals(1, support.beforeWorldAddInvocations);
        assertEquals(1, levelHandle.addFreshEntityInvocations);
        assertSame(CreatureSpawnEvent.SpawnReason.CUSTOM, levelHandle.lastSpawnReason);
    }

    @Test
    void shouldDispatchLegacyReplacementThroughLegacyPublicationBackend() {
        RecordingLegacyReplacementSupport support = new RecordingLegacyReplacementSupport();
        Entity entity = Mockito.mock(Entity.class);
        RuntimeAttachedEntityLifecycle<Entity> lifecycle = legacyLifecycle(EntityPublicationFamily.LEGACY_WORLD_LISTENER);

        ControlledEntity<Entity> attached = new LegacyReplacementStrategy_1_8_to_1_12("legacy-test").attach(
                support,
                entity,
                lifecycle
        );

        assertSame(lifecycle.handle(), attached);
        assertEquals(
                Arrays.asList(
                        "rebind-bukkit:legacy-world-listener",
                        "rebind-bridge:legacy-world-listener",
                        "replace-world:legacy-world-listener",
                        "rewire-passengers:legacy-world-listener",
                        "refresh-bukkit:legacy-world-listener",
                        "mark-removed:legacy-world-listener"
                ),
                support.events
        );
        assertEquals("legacy-entry", lifecycle.networkState().trackerEntryHandle());
    }

    @Test
    void shouldDispatchEntitiesByUuidReplacementThroughPublicationBackend() {
        assertPaperReplacementFamily(EntityPublicationFamily.ENTITIES_BY_UUID, MinecraftVersion.of(1, 16, 5));
    }

    @Test
    void shouldDispatchSectionManagerReplacementThroughPublicationBackend() {
        assertPaperReplacementFamily(EntityPublicationFamily.SECTION_MANAGER, MinecraftVersion.of(1, 17, 1));
    }

    @Test
    void shouldDispatchPaperChunkSystemReplacementThroughPublicationBackend() {
        assertPaperReplacementFamily(EntityPublicationFamily.PAPER_CHUNK_SYSTEM, MinecraftVersion.of(1, 19, 2));
    }

    @Test
    void shouldDispatchPaperMoonriseReplacementThroughPublicationBackend() {
        assertPaperReplacementFamily(EntityPublicationFamily.PAPER_MOONRISE_CHUNK_SYSTEM, MinecraftVersion.of(1, 21, 11));
    }

    private static void assertPaperReplacementFamily(
            @NotNull EntityPublicationFamily family,
            @NotNull MinecraftVersion version
    ) {
        RecordingPaperReplacementSupport support = new RecordingPaperReplacementSupport();
        support.expectedFamily = family;
        Entity entity = Mockito.mock(Entity.class);
        RuntimeAttachedEntityLifecycle<Entity> lifecycle = paperLifecycle(version, family);

        ControlledEntity<Entity> attached = new PaperReplacementStrategy_1_21_plus("paper-test").attach(
                support,
                entity,
                lifecycle
        );

        assertSame(lifecycle.handle(), attached);
        assertEquals(
                Arrays.asList(
                        "rebind-bukkit:" + family.id(),
                        "rebind-bridge:" + family.id(),
                        "replace-world:" + family.id(),
                        "rewire-passengers:" + family.id(),
                        "refresh-bukkit:" + family.id(),
                        "mark-removed:" + family.id()
                ),
                support.events
        );
        assertEquals("paper-entry", lifecycle.networkState().trackerEntryHandle());
        assertEquals("paper-state", lifecycle.networkState().trackerStateHandle());
    }

    private static RuntimeAttachedEntityLifecycle<Entity> legacyLifecycle(@NotNull EntityPublicationFamily family) {
        return new RuntimeAttachedEntityLifecycle<Entity>(
                CustomEntityBaseType.ZOMBIE,
                MinecraftVersion.of(1, 8, 8),
                nullController(),
                EntityTransportResolver.noop(),
                EntityPublicationBackendResolver.resolve(family)
        );
    }

    private static RuntimeAttachedEntityLifecycle<Entity> paperLifecycle(
            @NotNull MinecraftVersion version,
            @NotNull EntityPublicationFamily family
    ) {
        return new RuntimeAttachedEntityLifecycle<Entity>(
                CustomEntityBaseType.ZOMBIE,
                version,
                nullController(),
                EntityTransportResolver.noop(),
                EntityPublicationBackendResolver.resolve(family)
        );
    }

    @SuppressWarnings("unchecked")
    private static EntityController<Entity> nullController() {
        return (EntityController<Entity>) PassThroughEntityController.instance();
    }

    private static final class RecordingFreshSupport implements EntityPublicationFreshSupport {
        private final Object levelHandle;
        private int beforeWorldAddInvocations;

        private RecordingFreshSupport(@NotNull Object levelHandle) {
            this.levelHandle = levelHandle;
        }

        @Override
        public void beforeWorldAdd(@NotNull Object nativeEntity, @NotNull Location location) {
            beforeWorldAddInvocations++;
        }

        @Override
        public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
            return levelHandle;
        }
    }

    private static final class LegacyLevelHandle {
        private int addEntityInvocations;
        private int addFreshEntityInvocations;

        public boolean addEntity(Object entity) {
            addEntityInvocations++;
            return true;
        }

        public boolean addFreshEntity(Object entity) {
            addFreshEntityInvocations++;
            return true;
        }
    }

    private static final class ModernLevelHandle {
        private int addFreshEntityInvocations;
        private int addWithUuidInvocations;
        private int addEntityInvocations;

        public boolean addFreshEntity(Object entity) {
            addFreshEntityInvocations++;
            return true;
        }

        public boolean addWithUUID(Object entity) {
            addWithUuidInvocations++;
            return true;
        }

        public boolean addEntity(Object entity) {
            addEntityInvocations++;
            return true;
        }
    }

    private static final class ReasonedModernLevelHandle {
        private int addFreshEntityInvocations;
        private CreatureSpawnEvent.SpawnReason lastSpawnReason;

        public boolean addFreshEntity(Object entity, CreatureSpawnEvent.SpawnReason spawnReason) {
            addFreshEntityInvocations++;
            lastSpawnReason = spawnReason;
            return true;
        }
    }

    private static final class RecordingLegacyReplacementSupport implements LegacyReplacementStrategy_1_8_to_1_12.Support {
        private final LegacyHandle currentHandle = new LegacyHandle();
        private final LegacyHandle replacementHandle = new LegacyHandle();
        private final List<String> events = new ArrayList<String>();

        @Override
        public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
            return currentHandle;
        }

        @Override
        public <T extends Entity> LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement prepareReplacement(
                @NotNull T entity,
                @NotNull Object currentNativeHandle
        ) {
            return new LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement(new Object());
        }

        @Override
        public @NotNull Object allocateReplacementHandle(
                LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement
        ) {
            return replacementHandle;
        }

        @Override
        public <T extends Entity> void bindLifecycleToReplacement(
                @NotNull Object replacementHandle,
                LegacyReplacementStrategy_1_8_to_1_12.PreparedReplacement preparedReplacement,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
            return Mockito.mock(Entity.class);
        }

        @Override
        public Object resolveTrackerEntryHandle(@NotNull Object replacementHandle) {
            return "legacy-entry";
        }

        @Override
        public <T extends Entity> void scheduleRepairPass(
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull T entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void rebindBukkitEntity(@NotNull Entity entity, @NotNull Object replacementHandle) {
            events.add("rebind-bukkit:legacy-world-listener");
        }

        @Override
        public void rebindBukkitBridge(
                @NotNull EntityPublicationFamily family,
                @NotNull Entity bukkitEntity,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
            events.add("rebind-bridge:" + family.id());
        }

        @Override
        public void replaceWorldReferences(
                @NotNull EntityPublicationFamily family,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
            events.add("replace-world:" + family.id());
        }

        @Override
        public void rewireVehicleAndPassengerReferences(
                @NotNull EntityPublicationFamily family,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
            events.add("rewire-passengers:" + family.id());
        }

        @Override
        public void refreshBukkitWrappers(@NotNull EntityPublicationFamily family, @NotNull Entity entity) {
            events.add("refresh-bukkit:" + family.id());
        }

        @Override
        public void markEntityRemoved(@NotNull EntityPublicationFamily family, @NotNull Object oldHandle) {
            events.add("mark-removed:" + family.id());
        }

        @Override
        public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
        }

        @Override
        public void rebindLegacyBukkitBridge(
                @NotNull Entity bukkitEntity,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void replaceLegacyWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        }

        @Override
        public void rewireLegacyVehicleAndPassengerReferences(
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void refreshLegacyBukkitWrappers(@NotNull Entity entity) {
        }

        @Override
        public void markLegacyEntityRemoved(@NotNull Object oldHandle) {
        }
    }

    private static final class RecordingPaperReplacementSupport implements PaperReplacementStrategy_1_21_plus.Support {
        private final PaperHandle currentHandle = new PaperHandle();
        private final PaperHandle replacementHandle = new PaperHandle();
        private final List<String> events = new ArrayList<String>();
        private EntityPublicationFamily expectedFamily;

        @Override
        public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
            return currentHandle;
        }

        @Override
        public <T extends Entity> PaperReplacementStrategy_1_21_plus.PreparedReplacement prepareReplacement(
                @NotNull T entity,
                @NotNull Object currentNativeHandle
        ) {
            return new PaperReplacementStrategy_1_21_plus.PreparedReplacement(new Object());
        }

        @Override
        public @NotNull Object allocateReplacementHandle(
                PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
        ) {
            return replacementHandle;
        }

        @Override
        public <T extends Entity> void bindLifecycleToReplacement(
                @NotNull Object replacementHandle,
                PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
        }

        @Override
        public @NotNull Entity resolveBukkitWrapper(@NotNull Object replacementHandle) {
            return Mockito.mock(Entity.class);
        }

        @Override
        public @NotNull PaperReplacementStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
                @NotNull Object replacementHandle
        ) {
            return new PaperReplacementStrategy_1_21_plus.TrackingHandles("paper-entry", "paper-state");
        }

        @Override
        public <T extends Entity> void scheduleRepairPass(
                @NotNull NativeEntityLifecycle<T> lifecycle,
                @NotNull T entity,
                @NotNull Object currentNativeHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void rebindBukkitEntity(@NotNull Entity entity, @NotNull Object replacementHandle) {
            events.add("rebind-bukkit:" + expectedFamily.id());
        }

        @Override
        public void rebindBukkitBridge(
                @NotNull EntityPublicationFamily family,
                @NotNull Entity bukkitEntity,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
            events.add("rebind-bridge:" + family.id());
        }

        @Override
        public void replaceWorldReferences(
                @NotNull EntityPublicationFamily family,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
            events.add("replace-world:" + family.id());
        }

        @Override
        public void rewireVehicleAndPassengerReferences(
                @NotNull EntityPublicationFamily family,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
            events.add("rewire-passengers:" + family.id());
        }

        @Override
        public void refreshBukkitWrappers(@NotNull EntityPublicationFamily family, @NotNull Entity entity) {
            events.add("refresh-bukkit:" + family.id());
        }

        @Override
        public void markEntityRemoved(@NotNull EntityPublicationFamily family, @NotNull Object oldHandle) {
            events.add("mark-removed:" + family.id());
        }

        @Override
        public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
        }

        @Override
        public void rebindModernBukkitBridge(
                @NotNull Entity bukkitEntity,
                @NotNull Object oldHandle,
                @NotNull Object replacementHandle
        ) {
        }

        @Override
        public void replaceModernWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        }

        @Override
        public void rewireModernVehicleAndPassengerReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        }

        @Override
        public void refreshModernBukkitWrappers(@NotNull Entity entity) {
        }

        @Override
        public void markModernEntityRemoved(@NotNull Object oldHandle) {
        }
    }

    private static final class LegacyHandle {
        public Object copiedValue = "legacy";
    }

    private static final class PaperHandle {
        public Object copiedValue = "paper";
    }
}
