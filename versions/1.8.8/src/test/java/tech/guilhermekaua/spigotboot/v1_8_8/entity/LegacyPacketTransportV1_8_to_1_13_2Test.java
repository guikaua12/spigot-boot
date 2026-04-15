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
package tech.guilhermekaua.spigotboot.v1_8_8.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityMetadataFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTrackerHookFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTransportFamily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

class LegacyPacketTransportV1_8_to_1_13_2Test {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 8, 8);

    @Test
    void shouldSendLegacyInitialViewerSnapshotPacketsFor1_8VehicleSemantics() {
        BoundLifecycle bound = bindLifecycle();
        TestPlayerConnection connection = new TestPlayerConnection();
        Player viewer = createViewer(connection);
        org.bukkit.entity.Entity vehicle = createVehicle(77);

        when(bound.zombie.getVehicle()).thenReturn(vehicle);
        bound.lifecycle.networkState().setLiveHeadYaw(110.0F);

        bound.lifecycle.registerViewer(viewer);

        assertEquals(
                Arrays.asList(
                        TestSpawnPacket.class,
                        PacketPlayOutEntityMetadata.class,
                        PacketPlayOutUpdateAttributes.class,
                        PacketPlayOutEntityEquipment.class,
                        PacketPlayOutEntityEquipment.class,
                        PacketPlayOutEntityEffect.class,
                        PacketPlayOutEntityVelocity.class,
                        PacketPlayOutAttachEntity.class,
                        PacketPlayOutEntityHeadRotation.class
                ),
                connection.packetTypes()
        );

        PacketPlayOutEntityMetadata metadataPacket = assertInstanceOf(
                PacketPlayOutEntityMetadata.class,
                connection.sentPackets.get(1)
        );
        PacketPlayOutAttachEntity attachPacket = assertInstanceOf(
                PacketPlayOutAttachEntity.class,
                connection.sentPackets.get(7)
        );

        assertEquals(42, metadataPacket.entityId);
        assertEquals(true, metadataPacket.initialSnapshot);
        assertEquals(0, attachPacket.attachType);
        assertSame(bound.nativeEntity, attachPacket.entity);
        assertSame(((HandleCarrier) vehicle).getHandle(), attachPacket.vehicle);
    }

    @Test
    void shouldSendRelativeAndAbsoluteLegacyMovementPacketsFor1_8() {
        BoundLifecycle bound = bindLifecycle();
        TestPlayerConnection connection = new TestPlayerConnection();
        Player viewer = createViewer(connection);
        org.bukkit.entity.Entity vehicle = createVehicle(77);

        when(bound.zombie.getVehicle()).thenReturn(vehicle);
        bound.lifecycle.registerViewer(viewer);
        connection.clear();

        bound.locationHolder[0] = new Location(bound.world, 13.0D, 66.0D, -1.0D, 120.0F, 15.0F);
        bound.velocityHolder[0] = new Vector(0.5D, 0.0D, 0.25D);
        bound.lifecycle.networkState().setSyncedPosition(12.0D, 64.0D, -4.0D);
        bound.lifecycle.networkState().setSyncedRotation(90.0F, 10.0F);
        bound.lifecycle.networkState().setSyncedVelocity(0.0D, 0.0D, 0.0D);
        bound.lifecycle.networkState().setSyncedHeadYaw(90.0F);
        bound.lifecycle.networkState().setTicksSinceAbsoluteSync(0);
        bound.lifecycle.dispatchTick(new NoOpTickInvoker());

        assertEquals(
                Arrays.asList(
                        PacketPlayOutAttachEntity.class,
                        PacketPlayOutEntity.PacketPlayOutRelEntityMove.class,
                        PacketPlayOutEntity.PacketPlayOutEntityLook.class,
                        PacketPlayOutEntityVelocity.class,
                        PacketPlayOutEntityMetadata.class,
                        PacketPlayOutEntityHeadRotation.class
                ),
                connection.packetTypes()
        );

        PacketPlayOutEntity.PacketPlayOutRelEntityMove relativeMove = assertInstanceOf(
                PacketPlayOutEntity.PacketPlayOutRelEntityMove.class,
                connection.sentPackets.get(1)
        );
        assertEquals((byte) 32, relativeMove.deltaX);
        assertEquals((byte) 64, relativeMove.deltaY);
        assertEquals((byte) 96, relativeMove.deltaZ);

        connection.clear();
        bound.locationHolder[0] = new Location(bound.world, 20.0D, 70.0D, 3.0D, 180.0F, 0.0F);
        bound.velocityHolder[0] = new Vector(1.0D, 0.0D, 0.0D);
        bound.lifecycle.networkState().setTicksSinceAbsoluteSync(tech.guilhermekaua.spigotboot.versions.api.EntityNetworkController.ABSOLUTE_RESYNC_INTERVAL);
        bound.lifecycle.dispatchTick(new NoOpTickInvoker());

        assertEquals(
                Arrays.asList(
                        PacketPlayOutAttachEntity.class,
                        PacketPlayOutEntityTeleport.class,
                        PacketPlayOutEntity.PacketPlayOutEntityLook.class,
                        PacketPlayOutEntityVelocity.class,
                        PacketPlayOutEntityMetadata.class,
                        PacketPlayOutEntityHeadRotation.class
                ),
                connection.packetTypes()
        );
    }

    private static BoundLifecycle bindLifecycle() {
        SpigotVersionAdapterV1_8_8 adapter = new SpigotVersionAdapterV1_8_8();
        EntityTransport transport = EntityTransportResolver.resolve(
                new VersionRuntimeProfile(VERSION, RuntimeServerFlavor.SPIGOT, false, false, false),
                new EntityNetworkRuntimeBundle(
                        EntityTrackerHookFamily.LEGACY_ENTRY_HOOK,
                        EntityPublicationFamily.LEGACY_WORLD_LISTENER,
                        EntityTransportFamily.LEGACY_1_8_TO_1_13_2,
                        EntityMetadataFamily.LEGACY_DATA_WATCHER
                ),
                adapter,
                adapter.entityNetworkMetadataContract()
        );
        RuntimeAttachedEntityLifecycle<Zombie> lifecycle = new RuntimeAttachedEntityLifecycle<Zombie>(
                CustomEntityBaseType.ZOMBIE,
                VERSION,
                new EntityController<Zombie>() {
                },
                transport
        );
        World world = Mockito.mock(World.class);
        Location[] locationHolder = new Location[]{new Location(world, 12.0D, 64.0D, -4.0D, 90.0F, 10.0F)};
        Vector[] velocityHolder = new Vector[]{new Vector(1.5D, 0.0D, -0.5D)};
        EntityZombie nativeEntity = new EntityZombie(42);
        nativeEntity.dataWatcher = new DataWatcher(
                Collections.<DataWatcherItem>singletonList(new DataWatcherItem(0, "watcher")),
                Collections.<DataWatcherItem>singletonList(new DataWatcherItem(0, "dirty"))
        );
        nativeEntity.attributeMap.attributes.add(new Object());
        nativeEntity.equipment[0] = new ItemStack("main");
        nativeEntity.equipment[4] = new ItemStack("helmet");
        nativeEntity.effects.add(new MobEffect("speed"));

        Zombie zombie = Mockito.mock(Zombie.class, Mockito.withSettings().extraInterfaces(HandleCarrier.class));
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getLocation()).thenAnswer(invocation -> locationHolder[0]);
        when(zombie.getVelocity()).thenAnswer(invocation -> velocityHolder[0]);
        when(((HandleCarrier) zombie).getHandle()).thenReturn(nativeEntity);

        lifecycle.bind(zombie);
        lifecycle.networkState().setTrackerEntryHandle(new TrackerEntry(new TestSpawnPacket(nativeEntity.getId())));
        lifecycle.networkState().setLiveHeadYaw(90.0F);
        return new BoundLifecycle(lifecycle, zombie, world, nativeEntity, locationHolder, velocityHolder);
    }

    private static Player createViewer(TestPlayerConnection connection) {
        Player viewer = Mockito.mock(Player.class, Mockito.withSettings().extraInterfaces(HandleCarrier.class));
        when(((HandleCarrier) viewer).getHandle()).thenReturn(new EntityPlayer(connection));
        return viewer;
    }

    private static org.bukkit.entity.Entity createVehicle(int entityId) {
        org.bukkit.entity.Entity vehicle = Mockito.mock(
                org.bukkit.entity.Entity.class,
                Mockito.withSettings().extraInterfaces(HandleCarrier.class)
        );
        when(vehicle.getEntityId()).thenReturn(entityId);
        when(((HandleCarrier) vehicle).getHandle()).thenReturn(new Entity(entityId));
        return vehicle;
    }

    public interface HandleCarrier {
        Object getHandle();
    }

    private static final class BoundLifecycle {
        private final RuntimeAttachedEntityLifecycle<Zombie> lifecycle;
        private final Zombie zombie;
        private final World world;
        private final EntityZombie nativeEntity;
        private final Location[] locationHolder;
        private final Vector[] velocityHolder;

        private BoundLifecycle(
                RuntimeAttachedEntityLifecycle<Zombie> lifecycle,
                Zombie zombie,
                World world,
                EntityZombie nativeEntity,
                Location[] locationHolder,
                Vector[] velocityHolder
        ) {
            this.lifecycle = lifecycle;
            this.zombie = zombie;
            this.world = world;
            this.nativeEntity = nativeEntity;
            this.locationHolder = locationHolder;
            this.velocityHolder = velocityHolder;
        }
    }

    private static final class NoOpTickInvoker implements ContextualBaseInvoker<EntityTickContext<Zombie>, Void> {
        @Override
        public Void invoke(@NotNull EntityTickContext<Zombie> context) {
            return null;
        }
    }
}

class Packet {
}

class TestSpawnPacket extends Packet {
    final int entityId;

    TestSpawnPacket(int entityId) {
        this.entityId = entityId;
    }
}

class PacketPlayOutEntityDestroy extends Packet {
    final int[] entityIds;

    PacketPlayOutEntityDestroy(int[] entityIds) {
        this.entityIds = entityIds;
    }
}

class PacketPlayOutEntityTeleport extends Packet {
    final int entityId;

    PacketPlayOutEntityTeleport(Entity entity) {
        this.entityId = entity.getId();
    }
}

class PacketPlayOutEntityMetadata extends Packet {
    final int entityId;
    final DataWatcher dataWatcher;
    final boolean initialSnapshot;

    PacketPlayOutEntityMetadata(int entityId, DataWatcher dataWatcher, boolean initialSnapshot) {
        this.entityId = entityId;
        this.dataWatcher = dataWatcher;
        this.initialSnapshot = initialSnapshot;
    }
}

class PacketPlayOutEntityVelocity extends Packet {
    final int entityId;
    final double velocityX;
    final double velocityY;
    final double velocityZ;

    PacketPlayOutEntityVelocity(int entityId, double velocityX, double velocityY, double velocityZ) {
        this.entityId = entityId;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
    }
}

class PacketPlayOutEntityHeadRotation extends Packet {
    final int entityId;
    final byte headYaw;

    PacketPlayOutEntityHeadRotation(Entity entity, byte headYaw) {
        this.entityId = entity.getId();
        this.headYaw = headYaw;
    }
}

class PacketPlayOutUpdateAttributes extends Packet {
    final int entityId;
    final Collection<?> attributes;

    PacketPlayOutUpdateAttributes(int entityId, Collection<?> attributes) {
        this.entityId = entityId;
        this.attributes = attributes;
    }
}

class PacketPlayOutEntityEffect extends Packet {
    final int entityId;
    final MobEffect effect;

    PacketPlayOutEntityEffect(int entityId, MobEffect effect) {
        this.entityId = entityId;
        this.effect = effect;
    }
}

class PacketPlayOutEntityEquipment extends Packet {
    final int entityId;
    final int slot;
    final ItemStack item;

    PacketPlayOutEntityEquipment(int entityId, int slot, ItemStack item) {
        this.entityId = entityId;
        this.slot = slot;
        this.item = item;
    }
}

class PacketPlayOutAttachEntity extends Packet {
    final int attachType;
    final Entity entity;
    final Entity vehicle;

    PacketPlayOutAttachEntity(int attachType, Entity entity, Entity vehicle) {
        this.attachType = attachType;
        this.entity = entity;
        this.vehicle = vehicle;
    }
}

class PacketPlayOutNamedEntitySpawn extends Packet {
    final int entityId;

    PacketPlayOutNamedEntitySpawn(EntityHuman entityHuman) {
        this.entityId = entityHuman.getId();
    }
}

class PacketPlayOutSpawnEntityLiving extends Packet {
    final int entityId;

    PacketPlayOutSpawnEntityLiving(EntityLiving entityLiving) {
        this.entityId = entityLiving.getId();
    }
}

class PacketPlayOutSpawnEntity extends Packet {
    final int entityId;
    final int typeId;
    final int data;

    PacketPlayOutSpawnEntity(Entity entity, int typeId) {
        this(entity, typeId, 0);
    }

    PacketPlayOutSpawnEntity(Entity entity, int typeId, int data) {
        this.entityId = entity.getId();
        this.typeId = typeId;
        this.data = data;
    }
}

class PacketPlayOutEntity {
    static class PacketPlayOutRelEntityMove extends Packet {
        final int entityId;
        final byte deltaX;
        final byte deltaY;
        final byte deltaZ;
        final boolean onGround;

        PacketPlayOutRelEntityMove(int entityId, byte deltaX, byte deltaY, byte deltaZ, boolean onGround) {
            this.entityId = entityId;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.deltaZ = deltaZ;
            this.onGround = onGround;
        }
    }

    static class PacketPlayOutEntityLook extends Packet {
        final int entityId;
        final byte yaw;
        final byte pitch;
        final boolean onGround;

        PacketPlayOutEntityLook(int entityId, byte yaw, byte pitch, boolean onGround) {
            this.entityId = entityId;
            this.yaw = yaw;
            this.pitch = pitch;
            this.onGround = onGround;
        }
    }
}

class DataWatcher {
    private final List<DataWatcherItem> all;
    private final List<DataWatcherItem> dirty;

    DataWatcher(List<DataWatcherItem> all, List<DataWatcherItem> dirty) {
        this.all = all;
        this.dirty = dirty;
    }

    public List<DataWatcherItem> c() {
        return all;
    }

    public List<DataWatcherItem> b() {
        return dirty;
    }
}

class DataWatcherItem {
    final int id;
    final Object value;

    DataWatcherItem(int id, Object value) {
        this.id = id;
        this.value = value;
    }
}

class AttributeMap {
    final List<Object> attributes = new ArrayList<Object>();

    public Collection<Object> getSynchronizedAttributes() {
        return attributes;
    }
}

class ItemStack {
    final String id;

    ItemStack(String id) {
        this.id = id;
    }
}

class MobEffect {
    final String id;

    MobEffect(String id) {
        this.id = id;
    }
}

class Entity {
    boolean onGround = true;
    DataWatcher dataWatcher = new DataWatcher(Collections.<DataWatcherItem>emptyList(), Collections.<DataWatcherItem>emptyList());
    private final int id;

    Entity(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public DataWatcher getDataWatcher() {
        return dataWatcher;
    }
}

class EntityLiving extends Entity {
    final AttributeMap attributeMap = new AttributeMap();
    final ItemStack[] equipment = new ItemStack[5];
    final List<MobEffect> effects = new ArrayList<MobEffect>();

    EntityLiving(int id) {
        super(id);
    }

    public AttributeMap getAttributeMap() {
        return attributeMap;
    }

    public ItemStack getEquipment(int slot) {
        return equipment[slot];
    }

    public Collection<MobEffect> getEffects() {
        return effects;
    }
}

class EntityHuman extends EntityLiving {
    EntityHuman(int id) {
        super(id);
    }
}

class EntityZombie extends EntityLiving {
    EntityZombie(int id) {
        super(id);
    }
}

class EntityPlayer extends EntityHuman {
    final TestPlayerConnection playerConnection;

    EntityPlayer(TestPlayerConnection playerConnection) {
        super(999);
        this.playerConnection = playerConnection;
    }
}

class TestPlayerConnection {
    final List<Packet> sentPackets = new ArrayList<Packet>();

    public void sendPacket(Packet packet) {
        sentPackets.add(packet);
    }

    public void clear() {
        sentPackets.clear();
    }

    public List<Class<?>> packetTypes() {
        List<Class<?>> types = new ArrayList<Class<?>>();
        for (Packet packet : sentPackets) {
            types.add(packet.getClass());
        }
        return types;
    }
}

class TrackerEntry {
    private final Packet spawnPacket;

    TrackerEntry(Packet spawnPacket) {
        this.spawnPacket = spawnPacket;
    }

    public Packet c() {
        return spawnPacket;
    }
}
