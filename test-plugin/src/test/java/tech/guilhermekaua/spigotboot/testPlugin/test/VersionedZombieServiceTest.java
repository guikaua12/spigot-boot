package tech.guilhermekaua.spigotboot.testPlugin.test;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.SpawnBuilder;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;
import tech.guilhermekaua.spigotboot.testPlugin.services.VersionedZombieService;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionedZombieServiceTest {
    private static final CustomEntityId DEMO_ENTITY_ID = CustomEntityId.of("test-plugin", "orbit-zombie");

    @Test
    void spawnDemoZombie_registersTheTemplateAndDelegatesToTheResolvedPlatform() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        VersionedZombieService service = new VersionedZombieService(plugin);
        VersionedEntityPlatform platform = mock(VersionedEntityPlatform.class);
        Player owner = mock(Player.class);
        Zombie zombie = mock(Zombie.class);
        @SuppressWarnings("unchecked")
        SpawnedEntity<Zombie> entity = mock(SpawnedEntity.class);
        World world = mock(World.class);

        UUID ownerId = UUID.randomUUID();
        Location ownerLocation = new Location(world, 12.0D, 64.0D, -8.0D);
        ownerLocation.setDirection(new Vector(1.0D, 0.0D, 0.0D));

        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getLocation()).thenAnswer(invocation -> ownerLocation.clone());
        when(platform.template(any(CustomEntityId.class))).thenReturn(null);
        when(entity.bukkitEntity()).thenReturn(zombie);
        when(platform.spawn(any(EntityTemplate.class), any(Location.class), any())).thenReturn(entity);

        try (MockedStatic<SpigotEntityBootstrap> mockedBootstrap = mockStatic(SpigotEntityBootstrap.class)) {
            mockedBootstrap.when(SpigotEntityBootstrap::boot).thenReturn(platform);

            Zombie spawnedZombie = service.spawnDemoZombie(owner);
            assertSame(zombie, spawnedZombie);
        }

        ArgumentCaptor<EntityTemplate> registeredTemplate = ArgumentCaptor.forClass(EntityTemplate.class);
        ArgumentCaptor<EntityTemplate> spawnedTemplate = ArgumentCaptor.forClass(EntityTemplate.class);
        ArgumentCaptor<Location> spawnLocation = ArgumentCaptor.forClass(Location.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<SpawnBuilder<Zombie>>> customizer = ArgumentCaptor.forClass(Consumer.class);

        verify(platform).register(registeredTemplate.capture());
        verify(platform).spawn(spawnedTemplate.capture(), spawnLocation.capture(), customizer.capture());

        EntityTemplate template = registeredTemplate.getValue();
        assertSame(template, spawnedTemplate.getValue());
        assertEquals(DEMO_ENTITY_ID, template.id());
        assertEquals(CustomEntityBaseType.ZOMBIE, template.baseType());

        SpawnBuilder<Zombie> builder = SpawnBuilder.fromTemplate(template, spawnLocation.getValue());
        customizer.getValue().accept(builder);

        assertEquals(ownerId, builder.spawnOptions().data().getRequired("trackedPlayerId", UUID.class));
        assertEquals(world, spawnLocation.getValue().getWorld());
        assertEquals(16.0D, spawnLocation.getValue().getX(), 1.0E-9D);
        assertEquals(64.5D, spawnLocation.getValue().getY(), 1.0E-9D);
        assertEquals(-8.0D, spawnLocation.getValue().getZ(), 1.0E-9D);
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
        SpawnedEntity<Zombie> firstEntity = mock(SpawnedEntity.class);
        @SuppressWarnings("unchecked")
        SpawnedEntity<Zombie> secondEntity = mock(SpawnedEntity.class);
        World world = mock(World.class);
        AtomicReference<EntityTemplate<?>> registeredTemplate = new AtomicReference<EntityTemplate<?>>();

        UUID ownerId = UUID.randomUUID();
        Location ownerLocation = new Location(world, 0.0D, 70.0D, 0.0D);
        ownerLocation.setDirection(new Vector(0.0D, 0.0D, 1.0D));

        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getLocation()).thenAnswer(invocation -> ownerLocation.clone());
        when(platform.template(any(CustomEntityId.class))).thenAnswer(invocation -> registeredTemplate.get());
        when(firstEntity.bukkitEntity()).thenReturn(firstZombie);
        when(secondEntity.bukkitEntity()).thenReturn(secondZombie);
        when(platform.spawn(any(EntityTemplate.class), any(Location.class), any()))
                .thenReturn(firstEntity)
                .thenReturn(secondEntity);
        doAnswer(invocation -> {
            registeredTemplate.set(invocation.getArgument(0));
            return null;
        }).when(platform).register(any(EntityTemplate.class));

        try (MockedStatic<SpigotEntityBootstrap> mockedBootstrap = mockStatic(SpigotEntityBootstrap.class)) {
            mockedBootstrap.when(SpigotEntityBootstrap::boot).thenReturn(platform);

            assertSame(firstZombie, service.spawnDemoZombie(owner));
            assertSame(secondZombie, service.spawnDemoZombie(owner));
        }

        verify(firstEntity).remove();
        verify(platform, times(1)).register(any(EntityTemplate.class));
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

    @Test
    void spawnDynamicDemoEntity_usesTheOneOffBaseTypeSpawnFlow() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        VersionedZombieService service = new VersionedZombieService(plugin);
        VersionedEntityPlatform platform = mock(VersionedEntityPlatform.class);
        Player owner = mock(Player.class);
        Entity entityView = mock(Entity.class);
        @SuppressWarnings("unchecked")
        SpawnedEntity<Entity> entity = mock(SpawnedEntity.class);
        World world = mock(World.class);

        Location ownerLocation = new Location(world, 5.0D, 65.0D, 3.0D);
        ownerLocation.setDirection(new Vector(1.0D, 0.0D, 0.0D));

        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getLocation()).thenAnswer(invocation -> ownerLocation.clone());
        when(platform.supports(CustomEntityBaseType.COW)).thenReturn(true);
        when(entity.bukkitEntity()).thenReturn(entityView);
        doReturn(entity).when(platform).spawn(any(CustomEntityBaseType.class), any(Location.class), any());

        try (MockedStatic<SpigotEntityBootstrap> mockedBootstrap = mockStatic(SpigotEntityBootstrap.class)) {
            mockedBootstrap.when(SpigotEntityBootstrap::boot).thenReturn(platform);

            assertSame(entityView, service.spawnDynamicDemoEntity(owner, CustomEntityBaseType.COW));
        }

        ArgumentCaptor<Location> spawnLocation = ArgumentCaptor.forClass(Location.class);
        verify(platform).spawn(any(CustomEntityBaseType.class), spawnLocation.capture(), any());
        verify(platform, never()).register(any(EntityTemplate.class));

        assertEquals(9.0D, spawnLocation.getValue().getX(), 1.0E-9D);
        assertEquals(65.5D, spawnLocation.getValue().getY(), 1.0E-9D);
        assertEquals(3.0D, spawnLocation.getValue().getZ(), 1.0E-9D);
    }

    @Test
    void spawnDynamicDemoEntity_rejectsUnsupportedBaseTypes() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        VersionedZombieService service = new VersionedZombieService(plugin);
        VersionedEntityPlatform platform = mock(VersionedEntityPlatform.class);
        Player owner = mock(Player.class);

        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(platform.supports(CustomEntityBaseType.COW)).thenReturn(false);

        try (MockedStatic<SpigotEntityBootstrap> mockedBootstrap = mockStatic(SpigotEntityBootstrap.class)) {
            mockedBootstrap.when(SpigotEntityBootstrap::boot).thenReturn(platform);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> service.spawnDynamicDemoEntity(owner, CustomEntityBaseType.COW)
            );

            assertTrue(exception.getMessage().contains("cow"));
        }
    }
}
