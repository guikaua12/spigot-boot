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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Shared reflective legacy transport bridge used by the 1.8.8-1.13.2 version families.
 *
 * @since 2.0.2
 */
public class ReflectiveLegacyTransportSupport implements LegacyTransportSupport {
    private static final String[] PLAYER_HANDLE_METHOD_NAMES = new String[]{"getHandle"};
    private static final String[] PLAYER_CONNECTION_FIELD_NAMES = new String[]{"playerConnection", "connection"};
    private static final String[] SEND_PACKET_METHOD_NAMES = new String[]{"sendPacket", "a", "queuePacket"};
    private static final String[] ENTITY_ID_METHOD_NAMES = new String[]{"getId"};
    private static final String[] DATA_WATCHER_METHOD_NAMES = new String[]{"getDataWatcher", "getEntityData"};
    private static final String[] ALL_WATCHER_ITEM_METHOD_NAMES = new String[]{"c", "getAll", "packAll"};
    private static final String[] DIRTY_WATCHER_ITEM_METHOD_NAMES = new String[]{"b", "getChanged", "packDirty"};
    private static final String[] ON_GROUND_FIELD_NAMES = new String[]{"onGround"};
    private static final String[] ATTRIBUTE_MAP_METHOD_NAMES = new String[]{"getAttributeMap", "getAttributes"};
    private static final String[] SYNCED_ATTRIBUTES_METHOD_NAMES = new String[]{"getSynchronizedAttributes", "getSyncableAttributes", "c"};
    private static final String[] ACTIVE_EFFECTS_METHOD_NAMES = new String[]{"getEffects", "getActiveEffects"};
    private static final String[] GET_VEHICLE_METHOD_NAMES = new String[]{"getVehicle"};
    private static final String[] GET_PASSENGERS_METHOD_NAMES = new String[]{"getPassengers"};
    private static final String[] GET_PASSENGER_METHOD_NAMES = new String[]{"getPassenger"};

    private final String id;
    private final RelativeMoveMode relativeMoveMode;
    private final EquipmentMode equipmentMode;
    private final PassengerMode passengerMode;

    /**
     * Creates a new reflective legacy transport bridge.
     *
     * @param id the stable bridge id
     * @param relativeMoveMode the relative move packet encoding mode
     * @param equipmentMode the equipment packet encoding mode
     * @param passengerMode the passenger and vehicle packet encoding mode
     */
    public ReflectiveLegacyTransportSupport(
            @NotNull String id,
            @NotNull RelativeMoveMode relativeMoveMode,
            @NotNull EquipmentMode equipmentMode,
            @NotNull PassengerMode passengerMode
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.relativeMoveMode = Objects.requireNonNull(relativeMoveMode, "relativeMoveMode cannot be null");
        this.equipmentMode = Objects.requireNonNull(equipmentMode, "equipmentMode cannot be null");
        this.passengerMode = Objects.requireNonNull(passengerMode, "passengerMode cannot be null");
    }

    @Override
    public @NotNull String id() {
        return id;
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

        Object trackerEntry = request.networkState().trackerEntryHandle();
        if (trackerEntry != null) {
            Method spawnMethod = ReflectionSupport.findNamedMethod(trackerEntry.getClass(), new String[]{"c", "e"});
            if (spawnMethod != null) {
                Object packet = ReflectionSupport.invoke(spawnMethod, trackerEntry);
                if (packet != null) {
                    return packet;
                }
            }
        }

        Class<?> entityHumanType = resolveRelativeClass(nativeEntity, "EntityHuman");
        if (entityHumanType.isAssignableFrom(nativeEntity.getClass())) {
            return instantiateCompatible(resolveRelativeClass(nativeEntity, "PacketPlayOutNamedEntitySpawn"), nativeEntity);
        }

        Class<?> entityLivingType = resolveRelativeClass(nativeEntity, "EntityLiving");
        if (entityLivingType.isAssignableFrom(nativeEntity.getClass())) {
            return instantiateCompatible(resolveRelativeClass(nativeEntity, "PacketPlayOutSpawnEntityLiving"), nativeEntity);
        }

        Integer entityTypeId = resolveLegacyEntityTypeId(request.entity().bukkitEntity());
        if (entityTypeId == null) {
            return null;
        }

        Class<?> packetClass = resolveRelativeClass(nativeEntity, "PacketPlayOutSpawnEntity");
        Object packet = instantiateCompatible(packetClass, nativeEntity, Integer.valueOf(entityTypeId.intValue()), Integer.valueOf(0));
        if (packet != null) {
            return packet;
        }
        return instantiateCompatible(packetClass, nativeEntity, Integer.valueOf(entityTypeId.intValue()));
    }

    @Override
    public @Nullable Object createDestroyPacket(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return null;
        }
        Class<?> packetClass = resolveRelativeClass(nativeEntity, "PacketPlayOutEntityDestroy");
        Object packet = instantiateCompatible(packetClass, new int[]{resolveEntityId(request)});
        if (packet != null) {
            return packet;
        }
        return instantiateCompatible(packetClass, Integer.valueOf(resolveEntityId(request)));
    }

    @Override
    public @Nullable Object createRelativeMovePacket(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return null;
        }
        return relativeMoveMode.createPacket(
                resolveRelativeClass(nativeEntity, "PacketPlayOutEntity$PacketPlayOutRelEntityMove"),
                resolveEntityId(request),
                request,
                isOnGround(request)
        );
    }

    @Override
    public @Nullable Object createAbsoluteMovePacket(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        return nativeEntity == null ? null : instantiateCompatible(resolveRelativeClass(nativeEntity, "PacketPlayOutEntityTeleport"), nativeEntity);
    }

    @Override
    public @Nullable Object createRotationPacket(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return null;
        }
        return instantiateCompatible(
                resolveRelativeClass(nativeEntity, "PacketPlayOutEntity$PacketPlayOutEntityLook"),
                Integer.valueOf(resolveEntityId(request)),
                Byte.valueOf(encodeAngle(request.networkState().liveYaw())),
                Byte.valueOf(encodeAngle(request.networkState().livePitch())),
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
        return instantiateCompatible(
                resolveRelativeClass(nativeEntity, "PacketPlayOutEntityHeadRotation"),
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
        Class<?> packetClass = resolveRelativeClass(nativeEntity, "PacketPlayOutEntityVelocity");
        Object packet = instantiateCompatible(
                packetClass,
                Integer.valueOf(resolveEntityId(request)),
                Double.valueOf(request.networkState().liveVelocityX()),
                Double.valueOf(request.networkState().liveVelocityY()),
                Double.valueOf(request.networkState().liveVelocityZ())
        );
        if (packet != null) {
            return packet;
        }
        return instantiateCompatible(packetClass, nativeEntity);
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

        if (passengerMode == PassengerMode.VEHICLE_ATTACH_ONLY) {
            Object vehicleHandle = resolveVehicleHandle(request.entity().bukkitEntity());
            if (!request.targetsTrackedViewers() && vehicleHandle == null) {
                return Collections.emptyList();
            }
            Object packet = instantiateCompatible(
                    resolveRelativeClass(nativeEntity, "PacketPlayOutAttachEntity"),
                    Integer.valueOf(0),
                    nativeEntity,
                    vehicleHandle
            );
            if (packet == null) {
                return Collections.emptyList();
            }
            List<Object> packets = new ArrayList<Object>(1);
            packets.add(packet);
            return packets;
        }

        List<Object> packets = new ArrayList<Object>(2);
        Class<?> mountPacketClass = resolveRelativeClass(nativeEntity, "PacketPlayOutMount");
        if (!state.passengerEntityIds().isEmpty() || request.targetsTrackedViewers()) {
            Object packet = instantiateCompatible(mountPacketClass, nativeEntity);
            if (packet != null) {
                packets.add(packet);
            }
        }

        Object vehicleHandle = resolveVehicleHandle(request.entity().bukkitEntity());
        if (vehicleHandle != null) {
            Object packet = instantiateCompatible(mountPacketClass, vehicleHandle);
            if (packet != null) {
                packets.add(packet);
            }
        }
        return packets;
    }

    @Override
    public @Nullable Object createMetadataPacket(
            @NotNull EntityTransportRequest request,
            @NotNull List<WatcherItem> items,
            boolean initialSnapshot
    ) {
        Objects.requireNonNull(items, "items cannot be null");
        if (items.isEmpty()) {
            return null;
        }

        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity == null) {
            return null;
        }
        Object dataWatcher = resolveDataWatcher(nativeEntity);
        if (dataWatcher == null) {
            return null;
        }
        return instantiateCompatible(
                resolveRelativeClass(nativeEntity, "PacketPlayOutEntityMetadata"),
                Integer.valueOf(resolveEntityId(request)),
                dataWatcher,
                Boolean.valueOf(initialSnapshot)
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
        if (nativeEntity == null) {
            return Collections.emptyList();
        }

        List<Object> packets = new ArrayList<Object>();
        Collection<?> attributes = resolveSynchronizedAttributes(nativeEntity);
        if (!attributes.isEmpty()) {
            Object packet = instantiateCompatible(
                    resolveRelativeClass(nativeEntity, "PacketPlayOutUpdateAttributes"),
                    Integer.valueOf(resolveEntityId(request)),
                    attributes
            );
            if (packet != null) {
                packets.add(packet);
            }
        }

        for (EquipmentEntry equipmentEntry : resolveEquipmentEntries(nativeEntity)) {
            Object packet = equipmentMode.createPacket(this, nativeEntity, resolveEntityId(request), equipmentEntry);
            if (packet != null) {
                packets.add(packet);
            }
        }

        for (Object effect : resolveActiveEffects(nativeEntity)) {
            Object packet = instantiateCompatible(
                    resolveRelativeClass(nativeEntity, "PacketPlayOutEntityEffect"),
                    Integer.valueOf(resolveEntityId(request)),
                    effect
            );
            if (packet != null) {
                packets.add(packet);
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
            throw new IllegalStateException("Could not resolve the player connection for legacy transport bridge '" + id + "'.");
        }

        Method sendMethod = ReflectionSupport.findCompatibleMethod(
                connection.getClass(),
                SEND_PACKET_METHOD_NAMES,
                packet.getClass()
        );
        if (sendMethod == null) {
            throw new IllegalStateException(
                    "Could not resolve a packet send method for legacy transport bridge '" + id + "'."
            );
        }
        ReflectionSupport.invoke(sendMethod, connection, packet);
    }

    @Override
    public void broadcastPacket(@NotNull EntityTransportRequest request, @NotNull Object packet) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(packet, "packet cannot be null");

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

    private int resolveEntityId(@NotNull EntityTransportRequest request) {
        Object nativeEntity = resolveNativeEntity(request);
        if (nativeEntity != null) {
            Method getIdMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), ENTITY_ID_METHOD_NAMES);
            if (getIdMethod != null) {
                Object value = ReflectionSupport.invoke(getIdMethod, nativeEntity);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }
        }
        return request.entity().bukkitEntity().getEntityId();
    }

    private int resolveLegacyEntityTypeId(@NotNull Entity entity) {
        Method getTypeIdMethod = ReflectionSupport.requireNamedMethod(entity.getType().getClass(), new String[]{"getTypeId"});
        Object value = ReflectionSupport.invoke(getTypeIdMethod, entity.getType());
        return ((Number) value).intValue();
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
            Field field = ReflectionSupport.findField(nativeEntity.getClass(), ON_GROUND_FIELD_NAMES);
            if (field == null) {
                return false;
            }
            return Boolean.TRUE.equals(ReflectionSupport.readField(field, nativeEntity));
        }
    }

    private @Nullable Object resolveDataWatcher(@NotNull Object nativeEntity) {
        Method method = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), DATA_WATCHER_METHOD_NAMES);
        return method == null ? null : ReflectionSupport.invoke(method, nativeEntity);
    }

    private @NotNull Collection<?> resolveSynchronizedAttributes(@NotNull Object nativeEntity) {
        Method getAttributeMapMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), ATTRIBUTE_MAP_METHOD_NAMES);
        if (getAttributeMapMethod == null) {
            return Collections.emptyList();
        }
        Object attributeMap = ReflectionSupport.invoke(getAttributeMapMethod, nativeEntity);
        if (attributeMap == null) {
            return Collections.emptyList();
        }

        Method synchronizedMethod = ReflectionSupport.findNamedMethod(attributeMap.getClass(), SYNCED_ATTRIBUTES_METHOD_NAMES);
        if (synchronizedMethod == null) {
            return Collections.emptyList();
        }
        Object value = ReflectionSupport.invoke(synchronizedMethod, attributeMap);
        return value instanceof Collection ? (Collection<?>) value : Collections.emptyList();
    }

    private @NotNull Collection<?> resolveActiveEffects(@NotNull Object nativeEntity) {
        Method method = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), ACTIVE_EFFECTS_METHOD_NAMES);
        if (method == null) {
            return Collections.emptyList();
        }
        Object value = ReflectionSupport.invoke(method, nativeEntity);
        return value instanceof Collection ? (Collection<?>) value : Collections.emptyList();
    }

    private @NotNull List<EquipmentEntry> resolveEquipmentEntries(@NotNull Object nativeEntity) {
        return equipmentMode.resolveEntries(this, nativeEntity);
    }

    private @Nullable Object resolveVehicleHandle(@NotNull Entity entity) {
        Method method = ReflectionSupport.findNamedMethod(entity.getClass(), GET_VEHICLE_METHOD_NAMES);
        if (method == null) {
            return null;
        }
        Object vehicle = ReflectionSupport.invoke(method, entity);
        if (!(vehicle instanceof Entity)) {
            return null;
        }
        Method getHandleMethod = ReflectionSupport.findNamedMethod(vehicle.getClass(), PLAYER_HANDLE_METHOD_NAMES);
        return getHandleMethod == null ? null : ReflectionSupport.invoke(getHandleMethod, vehicle);
    }

    private @NotNull List<Entity> resolvePassengers(@NotNull Entity entity) {
        Method pluralMethod = ReflectionSupport.findNamedMethod(entity.getClass(), GET_PASSENGERS_METHOD_NAMES);
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

        Method singularMethod = ReflectionSupport.findNamedMethod(entity.getClass(), GET_PASSENGER_METHOD_NAMES);
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

    private byte encodeAngle(float angle) {
        return (byte) ((angle % 360.0F) * 256.0F / 360.0F);
    }

    private @NotNull Class<?> resolveRelativeClass(@NotNull Object anchor, @NotNull String binarySimpleName) {
        return resolveRelativeClass(anchor.getClass(), binarySimpleName);
    }

    private @NotNull Class<?> resolveRelativeClass(@NotNull Class<?> anchorType, @NotNull String binarySimpleName) {
        Package declaredPackage = anchorType.getPackage();
        if (declaredPackage == null) {
            throw new IllegalStateException("Could not resolve the native package for legacy transport bridge '" + id + "'.");
        }
        return ReflectionSupport.requireClass(declaredPackage.getName() + "." + binarySimpleName);
    }

    private @Nullable Object instantiateCompatible(@NotNull Class<?> type, @Nullable Object... arguments) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != arguments.length) {
                continue;
            }

            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                Object argument = arguments[index];
                if (argument == null) {
                    continue;
                }
                if (!wrap(parameterTypes[index]).isAssignableFrom(wrap(argument.getClass()))) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }

            constructor.setAccessible(true);
            return ReflectionSupport.instantiate(constructor, arguments);
        }
        return null;
    }

    private @NotNull Class<?> wrap(@NotNull Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        return type;
    }

    private @Nullable Object resolveFirstFieldValue(@NotNull Object target, @NotNull String[] fieldNames) {
        for (String fieldName : fieldNames) {
            Field field = ReflectionSupport.findField(target.getClass(), fieldName);
            if (field == null) {
                continue;
            }
            return ReflectionSupport.readField(field, target);
        }
        return null;
    }

    /**
     * Relative move encoding used by one legacy packet family.
     */
    public enum RelativeMoveMode {
        BYTE_DELTA_32 {
            @Override
            Object createPacket(
                    @NotNull Class<?> packetClass,
                    int entityId,
                    @NotNull EntityTransportRequest request,
                    boolean onGround
            ) {
                return ReflectiveLegacyTransportSupport.instantiateStatic(
                        packetClass,
                        Integer.valueOf(entityId),
                        Byte.valueOf(encodeByteDelta(request.networkState().liveX(), request.networkState().syncedX())),
                        Byte.valueOf(encodeByteDelta(request.networkState().liveY(), request.networkState().syncedY())),
                        Byte.valueOf(encodeByteDelta(request.networkState().liveZ(), request.networkState().syncedZ())),
                        Boolean.valueOf(onGround)
                );
            }

            private byte encodeByteDelta(double live, double synced) {
                return (byte) (((int) Math.floor(live * 32.0D)) - ((int) Math.floor(synced * 32.0D)));
            }
        },
        LONG_DELTA_4096 {
            @Override
            Object createPacket(
                    @NotNull Class<?> packetClass,
                    int entityId,
                    @NotNull EntityTransportRequest request,
                    boolean onGround
            ) {
                return ReflectiveLegacyTransportSupport.instantiateStatic(
                        packetClass,
                        Integer.valueOf(entityId),
                        Long.valueOf(encodeLongDelta(request.networkState().liveX(), request.networkState().syncedX())),
                        Long.valueOf(encodeLongDelta(request.networkState().liveY(), request.networkState().syncedY())),
                        Long.valueOf(encodeLongDelta(request.networkState().liveZ(), request.networkState().syncedZ())),
                        Boolean.valueOf(onGround)
                );
            }

            private long encodeLongDelta(double live, double synced) {
                return ((long) Math.floor(live * 4096.0D)) - ((long) Math.floor(synced * 4096.0D));
            }
        };

        abstract Object createPacket(
                @NotNull Class<?> packetClass,
                int entityId,
                @NotNull EntityTransportRequest request,
                boolean onGround
        );
    }

    /**
     * Equipment packet encoding used by one legacy packet family.
     */
    public enum EquipmentMode {
        INTEGER_SLOT {
            @Override
            List<EquipmentEntry> resolveEntries(@NotNull ReflectiveLegacyTransportSupport support, @NotNull Object nativeEntity) {
                Method getEquipmentMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"getEquipment"}, Integer.TYPE);
                if (getEquipmentMethod == null) {
                    return Collections.emptyList();
                }

                List<EquipmentEntry> entries = new ArrayList<EquipmentEntry>();
                for (int slot = 0; slot < 5; slot++) {
                    Object item = ReflectionSupport.invoke(getEquipmentMethod, nativeEntity, Integer.valueOf(slot));
                    if (!support.isEmptyItem(item)) {
                        entries.add(new EquipmentEntry(String.valueOf(slot), item));
                    }
                }
                return entries;
            }

            @Override
            Object createPacket(
                    @NotNull ReflectiveLegacyTransportSupport support,
                    @NotNull Object nativeEntity,
                    int entityId,
                    @NotNull EquipmentEntry equipmentEntry
            ) {
                return support.instantiateCompatible(
                        support.resolveRelativeClass(nativeEntity, "PacketPlayOutEntityEquipment"),
                        Integer.valueOf(entityId),
                        Integer.valueOf(Integer.parseInt(equipmentEntry.slot())),
                        equipmentEntry.item()
                );
            }
        },
        ENUM_ITEM_SLOT {
            @Override
            List<EquipmentEntry> resolveEntries(@NotNull ReflectiveLegacyTransportSupport support, @NotNull Object nativeEntity) {
                Class<?> enumItemSlotType = support.resolveRelativeClass(nativeEntity, "EnumItemSlot");
                Method valuesMethod = ReflectionSupport.requireNamedMethod(enumItemSlotType, new String[]{"values"});
                Method getEquipmentMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"getEquipment"}, enumItemSlotType);
                if (getEquipmentMethod == null) {
                    return Collections.emptyList();
                }

                Object[] slots = (Object[]) ReflectionSupport.invoke(valuesMethod, null);
                List<EquipmentEntry> entries = new ArrayList<EquipmentEntry>();
                for (Object slot : slots) {
                    Object item = ReflectionSupport.invoke(getEquipmentMethod, nativeEntity, slot);
                    if (!support.isEmptyItem(item)) {
                        entries.add(new EquipmentEntry(((Enum<?>) slot).name(), item));
                    }
                }
                return entries;
            }

            @Override
            Object createPacket(
                    @NotNull ReflectiveLegacyTransportSupport support,
                    @NotNull Object nativeEntity,
                    int entityId,
                    @NotNull EquipmentEntry equipmentEntry
            ) {
                Class<?> enumItemSlotType = support.resolveRelativeClass(nativeEntity, "EnumItemSlot");
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object slot = Enum.valueOf((Class<? extends Enum>) enumItemSlotType, equipmentEntry.slot());
                return support.instantiateCompatible(
                        support.resolveRelativeClass(nativeEntity, "PacketPlayOutEntityEquipment"),
                        Integer.valueOf(entityId),
                        slot,
                        equipmentEntry.item()
                );
            }
        };

        abstract List<EquipmentEntry> resolveEntries(
                @NotNull ReflectiveLegacyTransportSupport support,
                @NotNull Object nativeEntity
        );

        abstract Object createPacket(
                @NotNull ReflectiveLegacyTransportSupport support,
                @NotNull Object nativeEntity,
                int entityId,
                @NotNull EquipmentEntry equipmentEntry
        );
    }

    /**
     * Passenger and vehicle packet encoding used by one legacy packet family.
     */
    public enum PassengerMode {
        VEHICLE_ATTACH_ONLY,
        MOUNT_PACKET
    }

    private static @Nullable Object instantiateStatic(@NotNull Class<?> type, @Nullable Object... arguments) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != arguments.length) {
                continue;
            }

            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                Object argument = arguments[index];
                if (argument == null) {
                    continue;
                }
                if (!wrapStatic(parameterTypes[index]).isAssignableFrom(wrapStatic(argument.getClass()))) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }

            constructor.setAccessible(true);
            return ReflectionSupport.instantiate(constructor, arguments);
        }
        return null;
    }

    private static @NotNull Class<?> wrapStatic(@NotNull Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        return type;
    }

    private boolean isEmptyItem(@Nullable Object item) {
        if (item == null) {
            return true;
        }
        Method isEmptyMethod = ReflectionSupport.findNamedMethod(item.getClass(), new String[]{"isEmpty"});
        return isEmptyMethod != null && Boolean.TRUE.equals(ReflectionSupport.invoke(isEmptyMethod, item));
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
            if (!(request.entity().bukkitEntity() instanceof LivingEntity) || nativeEntity == null) {
                return LivingEntityMetadata.empty();
            }

            List<LivingAttribute> attributes = new ArrayList<LivingAttribute>();
            int index = 0;
            for (Object ignored : resolveSynchronizedAttributes(nativeEntity)) {
                attributes.add(new LivingAttribute("legacy-attribute-" + index, 0.0D));
                index++;
            }

            List<ActiveEffect> effects = new ArrayList<ActiveEffect>();
            index = 0;
            for (Object ignored : resolveActiveEffects(nativeEntity)) {
                effects.add(new ActiveEffect("legacy-effect-" + index, 0, 0, false, false));
                index++;
            }

            return new LivingEntityMetadata(
                    new LivingAttributeSnapshot(attributes),
                    new EquipmentSnapshot(resolveEquipmentEntries(nativeEntity)),
                    new ActiveEffectsSnapshot(effects)
            );
        }

        @Override
        public @NotNull HeadRotation headRotation() {
            return HeadRotation.of(request.networkState().liveHeadYaw());
        }

        @Override
        public @NotNull PassengerVehicleState passengerVehicleState() {
            List<Integer> passengerIds = new ArrayList<Integer>();
            for (Entity passenger : resolvePassengers(request.entity().bukkitEntity())) {
                passengerIds.add(Integer.valueOf(passenger.getEntityId()));
            }
            Entity vehicle = request.entity().bukkitEntity().getVehicle();
            return new PassengerVehicleState(
                    vehicle == null ? null : Integer.valueOf(vehicle.getEntityId()),
                    passengerIds
            );
        }

        private @NotNull List<WatcherItem> resolveWatcherItems(boolean fullSnapshot) {
            if (nativeEntity == null) {
                return Collections.emptyList();
            }
            Object dataWatcher = resolveDataWatcher(nativeEntity);
            if (dataWatcher == null) {
                return Collections.emptyList();
            }
            Object raw = invokeFirstListMethod(
                    dataWatcher,
                    fullSnapshot ? ALL_WATCHER_ITEM_METHOD_NAMES : DIRTY_WATCHER_ITEM_METHOD_NAMES
            );
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
    }
}
