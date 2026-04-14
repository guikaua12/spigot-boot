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
package tech.guilhermekaua.spigotboot.entity.runtime.network.transport;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.ActiveEffect;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.ActiveEffectsSnapshot;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EntityNetworkMetadataSource;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EquipmentEntry;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.EquipmentSnapshot;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.HeadRotation;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.LivingAttribute;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.LivingAttributeSnapshot;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.LivingEntityMetadata;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.PassengerVehicleState;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.WatcherDelta;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.WatcherItem;
import tech.guilhermekaua.spigotboot.entity.runtime.network.metadata.WatcherPayload;
import tech.guilhermekaua.spigotboot.entity.runtime.selection.EntityTransportFamily;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared reflective modern transport bridge used by the 1.14+ version families.
 *
 * @since 2.0.2
 */
public final class ReflectiveModernTransportSupport implements ModernTransportSupport {
    private static final String[] CRAFT_ITEM_STACK_SUFFIXES = new String[]{"inventory.CraftItemStack", "inventory.CraftItemStack"};
    private static final String[] ENTITY_DESTROY_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntityDestroy",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntityDestroy",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntityDestroy"
    };
    private static final String[] SPAWN_LIVING_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLiving",
            "net.minecraft.server.v1_16_R3.PacketPlayOutSpawnEntityLiving",
            "net.minecraft.server.v1_15_R1.PacketPlayOutSpawnEntityLiving",
            "net.minecraft.server.v1_14_R1.PacketPlayOutSpawnEntityLiving"
    };
    private static final String[] SPAWN_GENERIC_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundAddEntityPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity",
            "net.minecraft.server.v1_16_R3.PacketPlayOutSpawnEntity",
            "net.minecraft.server.v1_15_R1.PacketPlayOutSpawnEntity",
            "net.minecraft.server.v1_14_R1.PacketPlayOutSpawnEntity"
    };
    private static final String[] TELEPORT_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntityTeleport",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntityTeleport",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntityTeleport"
    };
    private static final String[] RELATIVE_MOVE_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Pos",
            "net.minecraft.network.protocol.game.PacketPlayOutEntity$PacketPlayOutRelEntityMove",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntity$PacketPlayOutRelEntityMove",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntity$PacketPlayOutRelEntityMove",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntity$PacketPlayOutRelEntityMove"
    };
    private static final String[] ROTATION_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot",
            "net.minecraft.network.protocol.game.PacketPlayOutEntity$PacketPlayOutEntityLook",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntity$PacketPlayOutEntityLook",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntity$PacketPlayOutEntityLook",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntity$PacketPlayOutEntityLook"
    };
    private static final String[] HEAD_ROTATION_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundRotateHeadPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutEntityHeadRotation",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntityHeadRotation",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntityHeadRotation",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntityHeadRotation"
    };
    private static final String[] VELOCITY_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutEntityVelocity",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntityVelocity",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntityVelocity",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntityVelocity"
    };
    private static final String[] PASSENGERS_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundSetPassengersPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutMount",
            "net.minecraft.server.v1_16_R3.PacketPlayOutMount",
            "net.minecraft.server.v1_15_R1.PacketPlayOutMount",
            "net.minecraft.server.v1_14_R1.PacketPlayOutMount"
    };
    private static final String[] METADATA_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntityMetadata",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntityMetadata",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntityMetadata"
    };
    private static final String[] UPDATE_ATTRIBUTES_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutUpdateAttributes",
            "net.minecraft.server.v1_16_R3.PacketPlayOutUpdateAttributes",
            "net.minecraft.server.v1_15_R1.PacketPlayOutUpdateAttributes",
            "net.minecraft.server.v1_14_R1.PacketPlayOutUpdateAttributes"
    };
    private static final String[] EQUIPMENT_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutEntityEquipment",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntityEquipment",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntityEquipment",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntityEquipment"
    };
    private static final String[] EFFECT_PACKET_TYPES = new String[]{
            "net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket",
            "net.minecraft.network.protocol.game.PacketPlayOutEntityEffect",
            "net.minecraft.server.v1_16_R3.PacketPlayOutEntityEffect",
            "net.minecraft.server.v1_15_R1.PacketPlayOutEntityEffect",
            "net.minecraft.server.v1_14_R1.PacketPlayOutEntityEffect"
    };
    private static final String[] POSITION_MOVE_ROTATION_TYPES = new String[]{
            "net.minecraft.world.entity.PositionMoveRotation"
    };
    private static final String[] VEC3_TYPES = new String[]{
            "net.minecraft.world.phys.Vec3"
    };
    private static final String[] DATA_WATCHER_METHOD_NAMES = new String[]{"getEntityData", "getDataWatcher"};
    private static final String[] ALL_WATCHER_ITEM_METHOD_NAMES = new String[]{"packAll", "getAll", "c", "getNonDefaultValues"};
    private static final String[] DIRTY_WATCHER_ITEM_METHOD_NAMES = new String[]{"packDirty", "packDirtyItems", "b", "getChanged"};
    private static final String[] ENTITY_ID_METHOD_NAMES = new String[]{"getId", "al", "an", "ar"};
    private static final String[] ENTITY_UUID_METHOD_NAMES = new String[]{"getUUID", "cs", "cu"};
    private static final String[] PLAYER_HANDLE_METHOD_NAMES = new String[]{"getHandle"};
    private static final String[] PLAYER_CONNECTION_FIELD_NAMES = new String[]{"connection", "playerConnection", "b", "c"};
    private static final String[] SEND_PACKET_METHOD_NAMES = new String[]{"sendPacket", "send", "a", "b"};
    private static final String[] DELTA_MOVEMENT_METHOD_NAMES = new String[]{"getDeltaMovement", "getMot", "dj", "dk", "du", "dv"};
    private static final String[] ON_GROUND_METHOD_NAMES = new String[]{"onGround", "isOnGround", "aV", "aG", "aI"};
    private static final String[] ACTIVE_EFFECTS_METHOD_NAMES = new String[]{"getActiveEffects", "getEffects", "ei", "ej"};
    private static final String[] ATTRIBUTE_MAP_METHOD_NAMES = new String[]{"getAttributes", "getAttributeMap", "ep", "eo"};
    private static final String[] SYNCED_ATTRIBUTES_METHOD_NAMES = new String[]{"getSyncableAttributes", "getSynchronizedAttributes", "getAttributesToSync"};
    private static final String[] BROADCAST_FIELD_NAMES = new String[]{"broadcastMethod", "broadcast"};
    private static final String[] PASSENGERS_FIELD_NAMES = new String[]{"opt_passengers", "passengers"};
    private static final String[] VEHICLE_FIELD_NAMES = new String[]{"opt_vehicle", "vehicle"};
    private static final String[] ZERO_FIELD_NAMES = new String[]{"ZERO", "b", "c"};

    private final String id;
    private final EntityTransportFamily family;
    private final MetadataPacketMode metadataPacketMode;
    private final boolean preferTrackerStateBroadcast;

    /**
     * Creates one reflective modern transport bridge.
     *
     * @param id the stable bridge id
     * @param family the implemented family
     * @param metadataPacketMode the metadata packet mode
     * @param preferTrackerStateBroadcast whether tracker-state broadcast should be preferred for tracked packets
     */
    public ReflectiveModernTransportSupport(
            @NotNull String id,
            @NotNull EntityTransportFamily family,
            @NotNull MetadataPacketMode metadataPacketMode,
            boolean preferTrackerStateBroadcast
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.family = Objects.requireNonNull(family, "family cannot be null");
        this.metadataPacketMode = Objects.requireNonNull(metadataPacketMode, "metadataPacketMode cannot be null");
        this.preferTrackerStateBroadcast = preferTrackerStateBroadcast;
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull EntityTransportFamily family() {
        return family;
    }

    @Override
    public @NotNull EntityNetworkMetadataSource metadataSource(@NotNull EntityTransportRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        return new ReflectiveMetadataSource(request, resolveNativeEntity(request));
    }

    @Override
    public @Nullable Object createSpawnPacket(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return null;
        }
        Object packet = tryInstantiateCompatible(SPAWN_LIVING_PACKET_TYPES, nativeEntity);
        if (packet != null) {
            return packet;
        }
        return tryInstantiateCompatible(SPAWN_GENERIC_PACKET_TYPES, nativeEntity);
    }

    @Override
    public @Nullable Object createDestroyPacket(@NotNull EntityTransportRequest request) {
        Integer entityId = resolveEntityId(request);
        if (entityId == null) {
            return null;
        }
        Object packet = tryInstantiateCompatible(ENTITY_DESTROY_PACKET_TYPES, Integer.TYPE, Integer.valueOf(entityId.intValue()));
        if (packet != null) {
            return packet;
        }
        return tryInstantiateCompatible(ENTITY_DESTROY_PACKET_TYPES, int[].class, new int[]{entityId.intValue()});
    }

    @Override
    public @Nullable Object createRelativeMovePacket(@NotNull EntityTransportRequest request) {
        Integer entityId = resolveEntityId(request);
        if (entityId == null) {
            return null;
        }
        short deltaX = encodeRelativeDelta(request.networkState().liveX() - request.networkState().syncedX());
        short deltaY = encodeRelativeDelta(request.networkState().liveY() - request.networkState().syncedY());
        short deltaZ = encodeRelativeDelta(request.networkState().liveZ() - request.networkState().syncedZ());
        return tryInstantiateCompatible(
                RELATIVE_MOVE_PACKET_TYPES,
                Integer.TYPE,
                Integer.valueOf(entityId.intValue()),
                Short.TYPE,
                Short.valueOf(deltaX),
                Short.TYPE,
                Short.valueOf(deltaY),
                Short.TYPE,
                Short.valueOf(deltaZ),
                Boolean.TYPE,
                Boolean.valueOf(isOnGround(request))
        );
    }

    @Override
    public @Nullable Object createAbsoluteMovePacket(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return null;
        }
        Object packet = tryInstantiateCompatible(TELEPORT_PACKET_TYPES, nativeEntity);
        if (packet != null) {
            return packet;
        }

        Integer entityId = resolveEntityId(request);
        Object positionMoveRotation = createPositionMoveRotation(request);
        if (entityId == null || positionMoveRotation == null) {
            return null;
        }
        return tryInstantiateCompatible(
                TELEPORT_PACKET_TYPES,
                Integer.TYPE,
                Integer.valueOf(entityId.intValue()),
                positionMoveRotation.getClass(),
                positionMoveRotation,
                Set.class,
                Collections.emptySet(),
                Boolean.TYPE,
                Boolean.valueOf(isOnGround(request))
        );
    }

    @Override
    public @Nullable Object createRotationPacket(@NotNull EntityTransportRequest request) {
        Integer entityId = resolveEntityId(request);
        if (entityId == null) {
            return null;
        }
        return tryInstantiateCompatible(
                ROTATION_PACKET_TYPES,
                Integer.TYPE,
                Integer.valueOf(entityId.intValue()),
                Byte.TYPE,
                Byte.valueOf(encodeAngle(request.networkState().liveYaw())),
                Byte.TYPE,
                Byte.valueOf(encodeAngle(request.networkState().livePitch())),
                Boolean.TYPE,
                Boolean.valueOf(isOnGround(request))
        );
    }

    @Override
    public @Nullable Object createHeadRotationPacket(
            @NotNull EntityTransportRequest request,
            @NotNull HeadRotation headRotation
    ) {
        Objects.requireNonNull(headRotation, "headRotation cannot be null");
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return null;
        }
        return tryInstantiateCompatible(
                HEAD_ROTATION_PACKET_TYPES,
                nativeEntity,
                Byte.valueOf(encodeAngle(headRotation.yaw()))
        );
    }

    @Override
    public @Nullable Object createVelocityPacket(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return null;
        }
        Object packet = tryInstantiateCompatible(VELOCITY_PACKET_TYPES, nativeEntity);
        if (packet != null) {
            return packet;
        }

        Integer entityId = resolveEntityId(request);
        if (entityId == null) {
            return null;
        }
        Object deltaMovement = resolveDeltaMovement(nativeEntity);
        if (deltaMovement != null) {
            packet = tryInstantiateCompatible(
                    VELOCITY_PACKET_TYPES,
                    Integer.TYPE,
                    Integer.valueOf(entityId.intValue()),
                    deltaMovement.getClass(),
                    deltaMovement
            );
            if (packet != null) {
                return packet;
            }
        }
        return tryInstantiateCompatible(
                VELOCITY_PACKET_TYPES,
                Integer.TYPE,
                Integer.valueOf(entityId.intValue()),
                Double.TYPE,
                Double.valueOf(request.networkState().liveVelocityX()),
                Double.TYPE,
                Double.valueOf(request.networkState().liveVelocityY()),
                Double.TYPE,
                Double.valueOf(request.networkState().liveVelocityZ())
        );
    }

    @Override
    public @NotNull List<Object> createPassengerVehiclePackets(
            @NotNull EntityTransportRequest request,
            @NotNull PassengerVehicleState state
    ) {
        Objects.requireNonNull(state, "state cannot be null");
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return Collections.emptyList();
        }
        Object packet = tryInstantiateCompatible(PASSENGERS_PACKET_TYPES, nativeEntity);
        if (packet == null) {
            return Collections.emptyList();
        }
        List<Object> packets = new ArrayList<Object>(1);
        packets.add(packet);
        return packets;
    }

    @Override
    public @Nullable Object createMetadataPacket(
            @NotNull EntityTransportRequest request,
            @NotNull List<WatcherItem> items,
            boolean initialSnapshot
    ) {
        Objects.requireNonNull(items, "items cannot be null");
        Integer entityId = resolveEntityId(request);
        if (entityId == null) {
            return null;
        }

        Object nativeEntity = resolveNativeEntity(request);
        Object dataWatcher = nativeEntity == null ? null : resolveDataWatcher(nativeEntity);
        if (metadataPacketMode == MetadataPacketMode.DATA_WATCHER_WITH_FLAG) {
            if (dataWatcher == null) {
                return null;
            }
            return tryInstantiateCompatible(
                    METADATA_PACKET_TYPES,
                    Integer.TYPE,
                    Integer.valueOf(entityId.intValue()),
                    dataWatcher.getClass(),
                    dataWatcher,
                    Boolean.TYPE,
                    Boolean.valueOf(initialSnapshot)
            );
        }

        List<Object> rawItems = extractRawWatcherItems(items);
        if (rawItems.isEmpty()) {
            return null;
        }
        return tryInstantiateCompatible(
                METADATA_PACKET_TYPES,
                Integer.TYPE,
                Integer.valueOf(entityId.intValue()),
                List.class,
                rawItems
        );
    }

    @Override
    public @NotNull List<Object> createLivingInitializationPackets(
            @NotNull EntityTransportRequest request,
            @NotNull LivingEntityMetadata livingMetadata
    ) {
        Objects.requireNonNull(livingMetadata, "livingMetadata cannot be null");
        if (!(request.entity().bukkitEntity() instanceof LivingEntity)) {
            return Collections.emptyList();
        }

        Object nativeEntity = resolveNativeEntity(request);
        Integer entityId = resolveEntityId(request);
        if (nativeEntity == null || entityId == null) {
            return Collections.emptyList();
        }

        List<Object> packets = new ArrayList<Object>();

        Collection<?> attributes = resolveSynchronizedAttributes(nativeEntity);
        if (!attributes.isEmpty()) {
            Object attributePacket = tryInstantiateCompatible(
                    UPDATE_ATTRIBUTES_PACKET_TYPES,
                    Integer.TYPE,
                    Integer.valueOf(entityId.intValue()),
                    Collection.class,
                    attributes
            );
            if (attributePacket != null) {
                packets.add(attributePacket);
            }
        }

        packets.addAll(createEquipmentPackets(entityId.intValue(), (LivingEntity) request.entity().bukkitEntity()));

        for (Object effect : resolveActiveEffects(nativeEntity)) {
            Object effectPacket = tryInstantiateCompatible(
                    EFFECT_PACKET_TYPES,
                    Integer.TYPE,
                    Integer.valueOf(entityId.intValue()),
                    effect.getClass(),
                    effect
            );
            if (effectPacket != null) {
                packets.add(effectPacket);
            }
        }

        return packets;
    }

    @Override
    public void sendPacket(@NotNull Player viewer, @NotNull Object packet) {
        Objects.requireNonNull(viewer, "viewer cannot be null");
        Objects.requireNonNull(packet, "packet cannot be null");

        Object playerHandle = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(viewer.getClass(), PLAYER_HANDLE_METHOD_NAMES),
                viewer
        );
        Object connection = resolveFirstFieldValue(playerHandle, PLAYER_CONNECTION_FIELD_NAMES);
        if (connection == null) {
            throw new IllegalStateException("Could not resolve the player connection for modern transport bridge '" + id + "'.");
        }

        Method sendMethod = ReflectionSupport.findCompatibleMethod(
                connection.getClass(),
                SEND_PACKET_METHOD_NAMES,
                packet.getClass()
        );
        if (sendMethod == null) {
            throw new IllegalStateException(
                    "Could not resolve a packet send method for modern transport bridge '" + id + "'."
            );
        }
        ReflectionSupport.invoke(sendMethod, connection, packet);
    }

    @Override
    public void broadcastPacket(@NotNull EntityTransportRequest request, @NotNull Object packet) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(packet, "packet cannot be null");

        if (preferTrackerStateBroadcast) {
            Consumer<Object> consumer = resolveBroadcastConsumer(request.networkState().trackerStateHandle());
            if (consumer != null) {
                consumer.accept(packet);
                return;
            }
        }

        for (Player viewer : request.networkState().viewers()) {
            sendPacket(viewer, packet);
        }
    }

    private @Nullable Object resolveNativeEntity(@NotNull EntityTransportRequest request) {
        Entity bukkitEntity = request.entity().bukkitEntity();
        Method getHandleMethod = ReflectionSupport.findNamedMethod(bukkitEntity.getClass(), PLAYER_HANDLE_METHOD_NAMES);
        if (getHandleMethod == null) {
            return null;
        }
        return ReflectionSupport.invoke(getHandleMethod, bukkitEntity);
    }

    private @Nullable Integer resolveEntityId(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity != null) {
            Method getIdMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), ENTITY_ID_METHOD_NAMES);
            if (getIdMethod != null) {
                Object value = ReflectionSupport.invoke(getIdMethod, nativeEntity);
                if (value instanceof Number) {
                    return Integer.valueOf(((Number) value).intValue());
                }
            }
        }
        return Integer.valueOf(request.entity().bukkitEntity().getEntityId());
    }

    private boolean isOnGround(@NotNull EntityTransportRequest request) {
        Entity bukkitEntity = request.entity().bukkitEntity();
        try {
            return bukkitEntity.isOnGround();
        } catch (LinkageError ignored) {
            Object nativeEntity = resolveNativeEntity(request);
            if (nativeEntity == null) {
                return false;
            }
            Method method = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), ON_GROUND_METHOD_NAMES);
            if (method == null) {
                return false;
            }
            Object value = ReflectionSupport.invoke(method, nativeEntity);
            return Boolean.TRUE.equals(value);
        }
    }

    private @Nullable Object resolveDataWatcher(@NotNull Object nativeEntity) {
        Method method = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), DATA_WATCHER_METHOD_NAMES);
        return method == null ? null : ReflectionSupport.invoke(method, nativeEntity);
    }

    private @Nullable Object resolveDeltaMovement(@NotNull Object nativeEntity) {
        Method method = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), DELTA_MOVEMENT_METHOD_NAMES);
        return method == null ? null : ReflectionSupport.invoke(method, nativeEntity);
    }

    private @Nullable Object createPositionMoveRotation(@NotNull EntityTransportRequest request) {
        Class<?> vec3Type;
        Class<?> positionMoveRotationType;
        try {
            vec3Type = ReflectionSupport.requireClass(VEC3_TYPES);
            positionMoveRotationType = ReflectionSupport.requireClass(POSITION_MOVE_ROTATION_TYPES);
        } catch (IllegalStateException ignored) {
            return null;
        }

        Constructor<?> vec3Constructor;
        Constructor<?> positionMoveRotationConstructor;
        try {
            vec3Constructor = ReflectionSupport.requireCompatibleConstructor(
                    vec3Type,
                    Double.TYPE,
                    Double.TYPE,
                    Double.TYPE
            );
            positionMoveRotationConstructor = ReflectionSupport.requireCompatibleConstructor(
                    positionMoveRotationType,
                    vec3Type,
                    vec3Type,
                    Float.TYPE,
                    Float.TYPE
            );
        } catch (IllegalStateException ignored) {
            return null;
        }

        Object position = ReflectionSupport.instantiate(
                vec3Constructor,
                Double.valueOf(request.networkState().liveX()),
                Double.valueOf(request.networkState().liveY()),
                Double.valueOf(request.networkState().liveZ())
        );
        Object zero = resolveZeroVector(vec3Type);
        if (zero == null) {
            zero = ReflectionSupport.instantiate(vec3Constructor, Double.valueOf(0.0D), Double.valueOf(0.0D), Double.valueOf(0.0D));
        }
        return ReflectionSupport.instantiate(
                positionMoveRotationConstructor,
                position,
                zero,
                Float.valueOf(request.networkState().liveYaw()),
                Float.valueOf(request.networkState().livePitch())
        );
    }

    private @Nullable Object resolveZeroVector(@NotNull Class<?> vec3Type) {
        for (String candidate : ZERO_FIELD_NAMES) {
            Field field = ReflectionSupport.findField(vec3Type, candidate);
            if (field != null) {
                return ReflectionSupport.readField(field, null);
            }
        }
        return null;
    }

    private @Nullable Consumer<Object> resolveBroadcastConsumer(@Nullable Object trackerStateHandle) {
        if (trackerStateHandle == null) {
            return null;
        }
        Field field = ReflectionSupport.findField(trackerStateHandle.getClass(), BROADCAST_FIELD_NAMES);
        if (field == null) {
            return null;
        }
        Object value = ReflectionSupport.readField(field, trackerStateHandle);
        if (!(value instanceof Consumer)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Consumer<Object> consumer = (Consumer<Object>) value;
        return consumer;
    }

    private @NotNull Collection<?> resolveSynchronizedAttributes(@NotNull Object nativeEntity) {
        Method mapMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), ATTRIBUTE_MAP_METHOD_NAMES);
        if (mapMethod == null) {
            return Collections.emptyList();
        }
        Object attributeMap = ReflectionSupport.invoke(mapMethod, nativeEntity);
        if (attributeMap == null) {
            return Collections.emptyList();
        }
        Method syncMethod = ReflectionSupport.findNamedMethod(attributeMap.getClass(), SYNCED_ATTRIBUTES_METHOD_NAMES);
        if (syncMethod == null) {
            return Collections.emptyList();
        }
        Object result = ReflectionSupport.invoke(syncMethod, attributeMap);
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        return Collections.emptyList();
    }

    private @NotNull Collection<?> resolveActiveEffects(@NotNull Object nativeEntity) {
        Method effectsMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), ACTIVE_EFFECTS_METHOD_NAMES);
        if (effectsMethod == null) {
            return Collections.emptyList();
        }
        Object result = ReflectionSupport.invoke(effectsMethod, nativeEntity);
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        if (result instanceof Map) {
            return ((Map<?, ?>) result).values();
        }
        return Collections.emptyList();
    }

    private @NotNull List<Object> createEquipmentPackets(int entityId, @NotNull LivingEntity livingEntity) {
        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment == null) {
            return Collections.emptyList();
        }

        Class<?> slotClass = resolveEquipmentSlotClass(livingEntity);
        if (slotClass == null) {
            return Collections.emptyList();
        }
        List<Object> nativePairs = new ArrayList<Object>();
        addEquipmentEntry(nativePairs, slotClass, readEquipmentItem(equipment, "getItemInMainHand", "getItemInHand"), 0, livingEntity);
        addEquipmentEntry(nativePairs, slotClass, readEquipmentItem(equipment, "getItemInOffHand"), 1, livingEntity);
        addEquipmentEntry(nativePairs, slotClass, readEquipmentItem(equipment, "getBoots"), 2, livingEntity);
        addEquipmentEntry(nativePairs, slotClass, readEquipmentItem(equipment, "getLeggings"), 3, livingEntity);
        addEquipmentEntry(nativePairs, slotClass, readEquipmentItem(equipment, "getChestplate"), 4, livingEntity);
        addEquipmentEntry(nativePairs, slotClass, readEquipmentItem(equipment, "getHelmet"), 5, livingEntity);
        if (nativePairs.isEmpty()) {
            return Collections.emptyList();
        }

        Object listPacket = tryInstantiateCompatible(
                EQUIPMENT_PACKET_TYPES,
                Integer.TYPE,
                Integer.valueOf(entityId),
                List.class,
                nativePairs
        );
        if (listPacket != null) {
            List<Object> packets = new ArrayList<Object>(1);
            packets.add(listPacket);
            return packets;
        }

        List<Object> packets = new ArrayList<Object>();
        for (Object pair : nativePairs) {
            Object slot = extractPairFirst(pair);
            Object item = extractPairSecond(pair);
            if (slot == null) {
                continue;
            }
            Object packet = tryInstantiateCompatible(
                    EQUIPMENT_PACKET_TYPES,
                    Integer.TYPE,
                    Integer.valueOf(entityId),
                    slot.getClass(),
                    slot,
                    item == null ? Object.class : item.getClass(),
                    item
            );
            if (packet != null) {
                packets.add(packet);
            }
        }
        return packets;
    }

    private void addEquipmentEntry(
            @NotNull List<Object> nativePairs,
            @NotNull Class<?> slotClass,
            @Nullable ItemStack item,
            int slotOrdinal,
            @NotNull LivingEntity livingEntity
    ) {
        if (item == null || isAir(item)) {
            return;
        }
        Object slot = resolveSlotConstant(slotClass, slotOrdinal);
        if (slot == null) {
            return;
        }
        Object nativeItem = asNativeItem(livingEntity, item);
        if (nativeItem == null) {
            return;
        }
        Object pair = createPair(slot, nativeItem);
        if (pair != null) {
            nativePairs.add(pair);
        }
    }

    private @Nullable ItemStack readEquipmentItem(@NotNull EntityEquipment equipment, @NotNull String... methodNames) {
        Method method = ReflectionSupport.findNamedMethod(equipment.getClass(), methodNames);
        if (method == null) {
            return null;
        }
        Object value = ReflectionSupport.invoke(method, equipment);
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    private boolean isAir(@NotNull ItemStack item) {
        return "AIR".equals(item.getType().name());
    }

    private @Nullable Object asNativeItem(@NotNull LivingEntity livingEntity, @NotNull ItemStack item) {
        String craftPackage = livingEntity.getClass().getPackage().getName();
        String packageRoot = craftPackage.endsWith(".entity")
                ? craftPackage.substring(0, craftPackage.length() - ".entity".length())
                : craftPackage;
        List<String> candidates = new ArrayList<String>();
        for (String suffix : CRAFT_ITEM_STACK_SUFFIXES) {
            candidates.add(packageRoot + "." + suffix);
        }
        try {
            Class<?> craftItemStackClass = ReflectionSupport.requireClass(candidates.toArray(new String[candidates.size()]));
            Method method = ReflectionSupport.requireNamedMethod(craftItemStackClass, new String[]{"asNMSCopy"}, ItemStack.class);
            return ReflectionSupport.invoke(method, null, item);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private @Nullable Class<?> resolveEquipmentSlotClass(@NotNull LivingEntity livingEntity) {
        Object nativeEntity = resolveNativeHandle(livingEntity);
        if (nativeEntity == null) {
            return null;
        }
        try {
            return ReflectionSupport.requireClass(
                    "net.minecraft.world.entity.EnumItemSlot",
                    "net.minecraft.world.entity.EquipmentSlot",
                    nativeEntity.getClass().getPackage().getName() + ".EnumItemSlot",
                    nativeEntity.getClass().getPackage().getName() + ".EquipmentSlot"
            );
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private @Nullable Object resolveNativeHandle(@NotNull Entity entity) {
        Method method = ReflectionSupport.findNamedMethod(entity.getClass(), PLAYER_HANDLE_METHOD_NAMES);
        return method == null ? null : ReflectionSupport.invoke(method, entity);
    }

    private @Nullable Object resolveSlotConstant(@NotNull Class<?> slotClass, int ordinal) {
        if (!slotClass.isEnum()) {
            return null;
        }
        Object[] constants = slotClass.getEnumConstants();
        if (constants == null || ordinal < 0 || ordinal >= constants.length) {
            return null;
        }
        return constants[ordinal];
    }

    private @Nullable Object createPair(@NotNull Object left, @Nullable Object right) {
        try {
            Class<?> pairClass = ReflectionSupport.requireClass("com.mojang.datafixers.util.Pair");
            Method ofMethod = ReflectionSupport.requireNamedMethod(pairClass, new String[]{"of"}, Object.class, Object.class);
            return ReflectionSupport.invoke(ofMethod, null, left, right);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private @Nullable Object extractPairFirst(@Nullable Object pair) {
        if (pair == null) {
            return null;
        }
        Method method = ReflectionSupport.findNamedMethod(pair.getClass(), new String[]{"getFirst", "getLeft"});
        return method == null ? null : ReflectionSupport.invoke(method, pair);
    }

    private @Nullable Object extractPairSecond(@Nullable Object pair) {
        if (pair == null) {
            return null;
        }
        Method method = ReflectionSupport.findNamedMethod(pair.getClass(), new String[]{"getSecond", "getRight"});
        return method == null ? null : ReflectionSupport.invoke(method, pair);
    }

    private @Nullable Object resolveFirstFieldValue(@NotNull Object target, @NotNull String[] candidateNames) {
        Field field = ReflectionSupport.findField(target.getClass(), candidateNames);
        return field == null ? null : ReflectionSupport.readField(field, target);
    }

    private @NotNull List<Object> extractRawWatcherItems(@NotNull List<WatcherItem> items) {
        List<Object> rawItems = new ArrayList<Object>(items.size());
        for (WatcherItem item : items) {
            if (item.value() != null) {
                rawItems.add(item.value());
            }
        }
        return rawItems;
    }

    private static short encodeRelativeDelta(double delta) {
        long encoded = Math.round(delta * 4096.0D);
        if (encoded < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        if (encoded > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        return (short) encoded;
    }

    private static byte encodeAngle(float degrees) {
        return (byte) Math.floor(degrees * 256.0F / 360.0F);
    }

    private @Nullable Object tryInstantiateCompatible(@NotNull String[] typeCandidates, @Nullable Object... values) {
        Class<?>[] argumentTypes = new Class<?>[values.length];
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            argumentTypes[index] = value == null ? Object.class : value.getClass();
        }
        return tryInstantiateCompatible(typeCandidates, argumentTypes, values);
    }

    private @Nullable Object tryInstantiateCompatible(
            @NotNull String[] typeCandidates,
            @NotNull Class<?> typeA,
            @NotNull Object valueA
    ) {
        return tryInstantiateCompatible(typeCandidates, new Class<?>[]{typeA}, new Object[]{valueA});
    }

    private @Nullable Object tryInstantiateCompatible(
            @NotNull String[] typeCandidates,
            @NotNull Class<?> typeA,
            @NotNull Object valueA,
            @NotNull Class<?> typeB,
            @NotNull Object valueB
    ) {
        return tryInstantiateCompatible(typeCandidates, new Class<?>[]{typeA, typeB}, new Object[]{valueA, valueB});
    }

    private @Nullable Object tryInstantiateCompatible(
            @NotNull String[] typeCandidates,
            @NotNull Class<?> typeA,
            @NotNull Object valueA,
            @NotNull Class<?> typeB,
            @NotNull Object valueB,
            @NotNull Class<?> typeC,
            @NotNull Object valueC,
            @NotNull Class<?> typeD,
            @NotNull Object valueD,
            @NotNull Class<?> typeE,
            @NotNull Object valueE,
            @NotNull Class<?> typeF,
            @NotNull Object valueF,
            @NotNull Class<?> typeG,
            @NotNull Object valueG
    ) {
        return tryInstantiateCompatible(
                typeCandidates,
                new Class<?>[]{typeA, typeB, typeC, typeD, typeE, typeF, typeG},
                new Object[]{valueA, valueB, valueC, valueD, valueE, valueF, valueG}
        );
    }

    private @Nullable Object tryInstantiateCompatible(
            @NotNull String[] typeCandidates,
            @NotNull Class<?> typeA,
            @NotNull Object valueA,
            @NotNull Class<?> typeB,
            @NotNull Object valueB,
            @NotNull Class<?> typeC,
            @NotNull Object valueC,
            @NotNull Class<?> typeD,
            @NotNull Object valueD,
            @NotNull Class<?> typeE,
            @NotNull Object valueE,
            @NotNull Class<?> typeF,
            @NotNull Object valueF,
            @NotNull Class<?> typeG,
            @NotNull Object valueG,
            @NotNull Class<?> typeH,
            @NotNull Object valueH,
            @NotNull Class<?> typeI,
            @NotNull Object valueI,
            @NotNull Class<?> typeJ,
            @NotNull Object valueJ
    ) {
        return tryInstantiateCompatible(
                typeCandidates,
                new Class<?>[]{typeA, typeB, typeC, typeD, typeE, typeF, typeG, typeH, typeI, typeJ},
                new Object[]{valueA, valueB, valueC, valueD, valueE, valueF, valueG, valueH, valueI, valueJ}
        );
    }

    private @Nullable Object tryInstantiateCompatible(
            @NotNull String[] typeCandidates,
            @NotNull Class<?>[] argumentTypes,
            @NotNull Object[] values
    ) {
        for (String typeCandidate : typeCandidates) {
            try {
                Class<?> type = ReflectionSupport.requireClass(typeCandidate);
                Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(type, argumentTypes);
                return ReflectionSupport.instantiate(constructor, values);
            } catch (IllegalStateException ignored) {
            }
        }
        return null;
    }

    /**
     * Packet layout mode used for metadata synchronization.
     *
     * @since 2.0.2
     */
    public enum MetadataPacketMode {
        /**
         * The metadata packet expects the native data watcher plus an initial/dirty flag.
         */
        DATA_WATCHER_WITH_FLAG,

        /**
         * The metadata packet expects a packed list of watcher items.
         */
        PACKED_ITEM_LIST
    }

    private final class ReflectiveMetadataSource implements EntityNetworkMetadataSource {
        private final EntityTransportRequest request;
        private final Object nativeEntity;

        private ReflectiveMetadataSource(@NotNull EntityTransportRequest request, @Nullable Object nativeEntity) {
            this.request = request;
            this.nativeEntity = nativeEntity;
        }

        @Override
        public @NotNull WatcherPayload watcherPayload() {
            return new WatcherPayload(resolveWatcherItems(true));
        }

        @Override
        public @NotNull WatcherDelta dirtyWatcherDelta() {
            return new WatcherDelta(resolveWatcherItems(false));
        }

        @Override
        public @NotNull LivingEntityMetadata livingMetadata() {
            if (!(request.entity().bukkitEntity() instanceof LivingEntity)) {
                return LivingEntityMetadata.empty();
            }
            LivingEntity livingEntity = (LivingEntity) request.entity().bukkitEntity();

            List<LivingAttribute> attributes = Collections.emptyList();

            List<EquipmentEntry> equipmentEntries = new ArrayList<EquipmentEntry>();
            EntityEquipment equipment = livingEntity.getEquipment();
            if (equipment != null) {
                ItemStack mainHand = readEquipmentItem(equipment, "getItemInMainHand", "getItemInHand");
                if (mainHand != null) {
                    equipmentEntries.add(new EquipmentEntry("mainhand", mainHand));
                }
                ItemStack offHand = readEquipmentItem(equipment, "getItemInOffHand");
                if (offHand != null) {
                    equipmentEntries.add(new EquipmentEntry("offhand", offHand));
                }
                ItemStack helmet = readEquipmentItem(equipment, "getHelmet");
                if (helmet != null) {
                    equipmentEntries.add(new EquipmentEntry("head", helmet));
                }
                ItemStack chestplate = readEquipmentItem(equipment, "getChestplate");
                if (chestplate != null) {
                    equipmentEntries.add(new EquipmentEntry("chest", chestplate));
                }
                ItemStack leggings = readEquipmentItem(equipment, "getLeggings");
                if (leggings != null) {
                    equipmentEntries.add(new EquipmentEntry("legs", leggings));
                }
                ItemStack boots = readEquipmentItem(equipment, "getBoots");
                if (boots != null) {
                    equipmentEntries.add(new EquipmentEntry("feet", boots));
                }
            }

            List<ActiveEffect> activeEffects = new ArrayList<ActiveEffect>();
            for (PotionEffect effect : livingEntity.getActivePotionEffects()) {
                String name = effect.getType().getName();
                activeEffects.add(
                        new ActiveEffect(
                                name == null ? "unknown" : name.toLowerCase(Locale.ENGLISH),
                                effect.getAmplifier(),
                                effect.getDuration(),
                                effect.isAmbient(),
                                effect.hasParticles()
                        )
                );
            }

            return new LivingEntityMetadata(
                    new LivingAttributeSnapshot(attributes),
                    new EquipmentSnapshot(equipmentEntries),
                    new ActiveEffectsSnapshot(activeEffects)
            );
        }

        @Override
        public @NotNull HeadRotation headRotation() {
            return HeadRotation.of(request.networkState().liveHeadYaw());
        }

        @Override
        public @NotNull PassengerVehicleState passengerVehicleState() {
            List<Integer> passengerIds = new ArrayList<Integer>();
            Object trackerStateHandle = request.networkState().trackerStateHandle();
            if (trackerStateHandle != null) {
                Field passengersField = ReflectionSupport.findField(trackerStateHandle.getClass(), PASSENGERS_FIELD_NAMES);
                if (passengersField != null) {
                    Object passengers = ReflectionSupport.readField(passengersField, trackerStateHandle);
                    if (passengers instanceof List) {
                        for (Object passenger : (List<?>) passengers) {
                            Integer passengerId = resolveEntityId(passenger);
                            if (passengerId != null) {
                                passengerIds.add(passengerId);
                            }
                        }
                    }
                }
                Field vehicleField = ReflectionSupport.findField(trackerStateHandle.getClass(), VEHICLE_FIELD_NAMES);
                Integer vehicleId = null;
                if (vehicleField != null) {
                    vehicleId = resolveEntityId(ReflectionSupport.readField(vehicleField, trackerStateHandle));
                }
                if (vehicleId != null || !passengerIds.isEmpty()) {
                    return new PassengerVehicleState(vehicleId, passengerIds);
                }
            }

            Entity vehicle = resolveVehicle(request.entity().bukkitEntity());
            for (Entity passenger : resolvePassengers(request.entity().bukkitEntity())) {
                passengerIds.add(Integer.valueOf(passenger.getEntityId()));
            }
            return new PassengerVehicleState(
                    vehicle == null ? null : Integer.valueOf(vehicle.getEntityId()),
                    passengerIds
            );
        }

        private @NotNull List<WatcherItem> resolveWatcherItems(boolean fullSnapshot) {
            if (nativeEntity == null) {
                return Collections.emptyList();
            }
            Object dataWatcher = ReflectiveModernTransportSupport.this.resolveDataWatcher(nativeEntity);
            if (dataWatcher == null) {
                return Collections.emptyList();
            }
            Object raw = fullSnapshot
                    ? invokeFirstListMethod(dataWatcher, ALL_WATCHER_ITEM_METHOD_NAMES)
                    : invokeFirstListMethod(dataWatcher, DIRTY_WATCHER_ITEM_METHOD_NAMES);
            if (!(raw instanceof List)) {
                return Collections.emptyList();
            }
            List<WatcherItem> items = new ArrayList<WatcherItem>();
            int index = 0;
            for (Object item : (List<?>) raw) {
                items.add(new WatcherItem(resolveWatcherIndex(item, index), item.getClass().getName(), item));
                index++;
            }
            return items;
        }

        private @Nullable Object invokeFirstListMethod(@NotNull Object target, @NotNull String[] candidates) {
            for (String candidate : candidates) {
                Method method = ReflectionSupport.findNamedMethod(target.getClass(), new String[]{candidate});
                if (method == null) {
                    continue;
                }
                Object value = ReflectionSupport.invoke(method, target);
                if (value instanceof List) {
                    return value;
                }
            }
            return null;
        }

        private int resolveWatcherIndex(@Nullable Object item, int fallbackIndex) {
            if (item == null) {
                return fallbackIndex;
            }
            Field field = ReflectionSupport.findField(item.getClass(), "id", "index", "a");
            if (field == null) {
                return fallbackIndex;
            }
            Object value = ReflectionSupport.readField(field, item);
            return value instanceof Number ? ((Number) value).intValue() : fallbackIndex;
        }

        private @Nullable Integer resolveEntityId(@Nullable Object nativeEntity) {
            if (nativeEntity == null) {
                return null;
            }
            Method method = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), ENTITY_ID_METHOD_NAMES);
            if (method == null) {
                return null;
            }
            Object value = ReflectionSupport.invoke(method, nativeEntity);
            return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
        }

        private @Nullable Entity resolveVehicle(@NotNull Entity entity) {
            Method method = ReflectionSupport.findNamedMethod(entity.getClass(), new String[]{"getVehicle"});
            if (method == null) {
                return null;
            }
            Object value = ReflectionSupport.invoke(method, entity);
            return value instanceof Entity ? (Entity) value : null;
        }

        private @NotNull List<Entity> resolvePassengers(@NotNull Entity entity) {
            Method pluralMethod = ReflectionSupport.findNamedMethod(entity.getClass(), new String[]{"getPassengers"});
            if (pluralMethod != null) {
                Object value = ReflectionSupport.invoke(pluralMethod, entity);
                if (value instanceof List) {
                    List<Entity> passengers = new ArrayList<Entity>();
                    for (Object passenger : (List<?>) value) {
                        if (passenger instanceof Entity) {
                            passengers.add((Entity) passenger);
                        }
                    }
                    return passengers;
                }
            }

            Method singularMethod = ReflectionSupport.findNamedMethod(entity.getClass(), new String[]{"getPassenger"});
            if (singularMethod == null) {
                return Collections.emptyList();
            }
            Object value = ReflectionSupport.invoke(singularMethod, entity);
            if (!(value instanceof Entity)) {
                return Collections.emptyList();
            }
            List<Entity> passengers = new ArrayList<Entity>(1);
            passengers.add((Entity) value);
            return passengers;
        }
    }
}
