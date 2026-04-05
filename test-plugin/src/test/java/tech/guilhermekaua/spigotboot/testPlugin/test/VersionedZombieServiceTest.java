package tech.guilhermekaua.spigotboot.testPlugin.test;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.ZombieEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;
import tech.guilhermekaua.spigotboot.testPlugin.services.VersionedZombieService;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionedZombieServiceTest {
    private static final CustomEntityId DEMO_ENTITY_ID = CustomEntityId.of("test-plugin", "orbit-zombie");

    @Test
    void spawnDemoZombie_registersTheDefinitionAndDelegatesToTheResolvedPlatform() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        VersionedZombieService service = new VersionedZombieService(plugin);
        VersionedEntityPlatform platform = mock(VersionedEntityPlatform.class);
        Player owner = mock(Player.class);
        Zombie zombie = mock(Zombie.class);
        @SuppressWarnings("unchecked")
        CustomEntityHandle<Zombie> handle = mock(CustomEntityHandle.class);
        World world = mock(World.class);

        UUID ownerId = UUID.randomUUID();
        Location ownerLocation = new Location(world, 12.0D, 64.0D, -8.0D);
        ownerLocation.setDirection(new Vector(1.0D, 0.0D, 0.0D));

        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getLocation()).thenAnswer(invocation -> ownerLocation.clone());
        when(platform.definition(any(CustomEntityId.class))).thenReturn(null);
        when(handle.bukkitEntity()).thenReturn(zombie);
        when(platform.spawn(any(ZombieEntityDefinition.class), any(CustomEntitySpawnRequest.class))).thenReturn(handle);

        try (MockedStatic<SpigotEntityBootstrap> mockedBootstrap = mockStatic(SpigotEntityBootstrap.class)) {
            mockedBootstrap.when(SpigotEntityBootstrap::boot).thenReturn(platform);

            Zombie spawnedZombie = service.spawnDemoZombie(owner);
            assertSame(zombie, spawnedZombie);
        }

        ArgumentCaptor<ZombieEntityDefinition> registeredDefinition = ArgumentCaptor.forClass(ZombieEntityDefinition.class);
        ArgumentCaptor<ZombieEntityDefinition> spawnedDefinition = ArgumentCaptor.forClass(ZombieEntityDefinition.class);
        ArgumentCaptor<CustomEntitySpawnRequest> spawnRequest = ArgumentCaptor.forClass(CustomEntitySpawnRequest.class);

        verify(platform).registerDefinition(registeredDefinition.capture());
        verify(platform).spawn(spawnedDefinition.capture(), spawnRequest.capture());

        ZombieEntityDefinition definition = registeredDefinition.getValue();
        assertSame(definition, spawnedDefinition.getValue());
        assertEquals(DEMO_ENTITY_ID, definition.id());
        assertEquals(CustomEntityBaseType.ZOMBIE, definition.baseType());

        CustomEntitySpawnRequest request = spawnRequest.getValue();
        assertEquals(ownerId, request.data().getRequired("trackedPlayerId", UUID.class));
        assertEquals(world, request.location().getWorld());
        assertEquals(16.0D, request.location().getX(), 1.0E-9D);
        assertEquals(64.5D, request.location().getY(), 1.0E-9D);
        assertEquals(-8.0D, request.location().getZ(), 1.0E-9D);
    }

    @Test
    void spawnDemoZombie_reusesTheResolvedPlatformAndRemovesThePreviousZombieForTheSameOwner() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        VersionedZombieService service = new VersionedZombieService(plugin);
        VersionedEntityPlatform platform = mock(VersionedEntityPlatform.class);
        Player owner = mock(Player.class);
        Zombie firstZombie = mock(Zombie.class);
        Zombie secondZombie = mock(Zombie.class);
        @SuppressWarnings("unchecked")
        CustomEntityHandle<Zombie> firstHandle = mock(CustomEntityHandle.class);
        @SuppressWarnings("unchecked")
        CustomEntityHandle<Zombie> secondHandle = mock(CustomEntityHandle.class);
        World world = mock(World.class);
        AtomicReference<ZombieEntityDefinition> registeredDefinition = new AtomicReference<ZombieEntityDefinition>();

        UUID ownerId = UUID.randomUUID();
        Location ownerLocation = new Location(world, 0.0D, 70.0D, 0.0D);
        ownerLocation.setDirection(new Vector(0.0D, 0.0D, 1.0D));

        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getLocation()).thenAnswer(invocation -> ownerLocation.clone());
        when(platform.definition(any(CustomEntityId.class))).thenAnswer(invocation -> registeredDefinition.get());
        when(firstHandle.bukkitEntity()).thenReturn(firstZombie);
        when(secondHandle.bukkitEntity()).thenReturn(secondZombie);
        when(platform.spawn(any(ZombieEntityDefinition.class), any(CustomEntitySpawnRequest.class)))
                .thenReturn(firstHandle)
                .thenReturn(secondHandle);
        doAnswer(invocation -> {
            registeredDefinition.set(invocation.getArgument(0));
            return null;
        }).when(platform).registerDefinition(any(ZombieEntityDefinition.class));

        try (MockedStatic<SpigotEntityBootstrap> mockedBootstrap = mockStatic(SpigotEntityBootstrap.class)) {
            mockedBootstrap.when(SpigotEntityBootstrap::boot).thenReturn(platform);

            assertSame(firstZombie, service.spawnDemoZombie(owner));
            assertSame(secondZombie, service.spawnDemoZombie(owner));
        }

        verify(firstHandle).remove();
        verify(platform, times(1)).registerDefinition(any(ZombieEntityDefinition.class));
    }

    @Test
    void spawnDemoZombie_wrapsBootFailuresWithASupportedVersionMessage() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        VersionedZombieService service = new VersionedZombieService(plugin);
        Player owner = mock(Player.class);

        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());

        try (MockedStatic<SpigotEntityBootstrap> mockedBootstrap = mockStatic(SpigotEntityBootstrap.class)) {
            mockedBootstrap.when(SpigotEntityBootstrap::boot).thenThrow(new RuntimeException("boom"));

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> service.spawnDemoZombie(owner)
            );

            assertTrue(exception.getMessage().contains("1.8.8 and 1.21.11"));
            assertNotNull(exception.getCause());
        }
    }
}
