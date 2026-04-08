package tech.guilhermekaua.spigotboot.testPlugin.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.testPlugin.entity.controller.OrbitingZombieController;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrbitingZombieControllerTest {

    @Test
    void onTick_orbitsTrackedPlayerAndFacesTheTarget() {
        UUID trackedPlayerId = UUID.randomUUID();
        OrbitingZombieController controller = new OrbitingZombieController(trackedPlayerId);

        World world = mock(World.class);
        Zombie zombie = mock(Zombie.class);
        @SuppressWarnings("unchecked")
        ControlledEntity<Zombie> entity = mock(ControlledEntity.class);
        @SuppressWarnings("unchecked")
        EntityBaseInvoker<Void> base = mock(EntityBaseInvoker.class);
        Player player = mock(Player.class);

        Location zombieLocation = new Location(world, 0.0D, 64.0D, 0.0D);
        Location playerLocation = new Location(world, 10.0D, 64.0D, -4.0D);
        playerLocation.setDirection(new Vector(0.0D, 0.0D, 1.0D));

        when(entity.bukkitEntity()).thenReturn(zombie);
        when(entity.isRemoved()).thenReturn(false);
        when(entity.baseType()).thenReturn(CustomEntityBaseType.ZOMBIE);
        when(entity.minecraftVersion()).thenReturn(MinecraftVersion.of(1, 21, 11));
        when(zombie.isValid()).thenReturn(true);
        when(zombie.isDead()).thenReturn(false);
        when(zombie.getWorld()).thenReturn(world);
        when(zombie.getLocation()).thenReturn(zombieLocation.clone());

        when(player.isOnline()).thenReturn(true);
        when(player.isDead()).thenReturn(false);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(invocation -> playerLocation.clone());

        EntityTickContext<Zombie> context = new EntityTickContext<Zombie>(entity, base);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(trackedPlayerId)).thenReturn(player);

            controller.onTick(context);
        }

        verify(base).invoke();
        ArgumentCaptor<Location> teleportCaptor = ArgumentCaptor.forClass(Location.class);
        verify(zombie).teleport(teleportCaptor.capture());
        verify(zombie).setTarget(player);

        Location orbitLocation = teleportCaptor.getValue();
        assertEquals(world, orbitLocation.getWorld());
        assertEquals(playerLocation.getY(), orbitLocation.getY(), 1.0E-9D);

        double distanceFromTarget = orbitLocation.distance(playerLocation);
        assertTrue(distanceFromTarget >= 1.7D);
        assertTrue(distanceFromTarget <= 3.1D);

        Vector expectedDirection = playerLocation.toVector().subtract(orbitLocation.toVector()).normalize();
        Vector actualDirection = orbitLocation.getDirection().normalize();
        assertTrue(expectedDirection.distance(actualDirection) < 1.0E-6D);
    }

    @Test
    void onTick_skipsWhenTrackedPlayerCannotBeResolved() {
        UUID trackedPlayerId = UUID.randomUUID();
        OrbitingZombieController controller = new OrbitingZombieController(trackedPlayerId);

        Zombie zombie = mock(Zombie.class);
        @SuppressWarnings("unchecked")
        ControlledEntity<Zombie> entity = mock(ControlledEntity.class);
        @SuppressWarnings("unchecked")
        EntityBaseInvoker<Void> base = mock(EntityBaseInvoker.class);

        when(entity.bukkitEntity()).thenReturn(zombie);
        when(entity.isRemoved()).thenReturn(false);
        when(entity.baseType()).thenReturn(CustomEntityBaseType.ZOMBIE);
        when(entity.minecraftVersion()).thenReturn(MinecraftVersion.of(1, 21, 11));
        when(zombie.isValid()).thenReturn(true);
        when(zombie.isDead()).thenReturn(false);

        EntityTickContext<Zombie> context = new EntityTickContext<Zombie>(entity, base);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getPlayer(trackedPlayerId)).thenReturn(null);

            controller.onTick(context);
        }

        verify(base).invoke();
        verify(zombie, never()).teleport(any(Location.class));
        verify(zombie, never()).setTarget(any(Player.class));
    }
}
