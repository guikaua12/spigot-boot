package tech.guilhermekaua.spigotboot.testPlugin.test;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.testPlugin.command.ZombieTestCommand;
import tech.guilhermekaua.spigotboot.testPlugin.services.VersionedZombieService;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZombieTestCommandTest {

    @Test
    void root_sendsUsageLines() {
        VersionedZombieService versionedZombieService = mock(VersionedZombieService.class);
        ZombieTestCommand command = new ZombieTestCommand(versionedZombieService);
        Player player = mock(Player.class);

        command.root(player);

        InOrder inOrder = inOrder(player);
        inOrder.verify(player).sendMessage(ChatColor.GREEN + "Zombie demo commands:");
        inOrder.verify(player).sendMessage(ChatColor.YELLOW + "/zombietest spawn " + ChatColor.GRAY + "- spawn the orbit zombie demo.");
        inOrder.verify(player).sendMessage(ChatColor.YELLOW + "/zombietest spawn-dynamic <baseType> " + ChatColor.GRAY + "- spawn a one-off demo for the typed base type.");
        inOrder.verify(player).sendMessage(ChatColor.YELLOW + "/zombietest clear-spawn " + ChatColor.GRAY + "- clear your spawned demo entity.");
        inOrder.verify(player).sendMessage(ChatColor.YELLOW + "/zombietest attach " + ChatColor.GRAY + "- attach hooks to the nearest zombie.");
        inOrder.verify(player).sendMessage(ChatColor.YELLOW + "/zombietest clear-attach " + ChatColor.GRAY + "- clear your attached zombie demo.");
    }

    @Test
    void spawn_spawnsTheDemoZombieAndReportsItsLocation() {
        VersionedZombieService versionedZombieService = mock(VersionedZombieService.class);
        ZombieTestCommand command = new ZombieTestCommand(versionedZombieService);
        Player player = mock(Player.class);
        Zombie zombie = mock(Zombie.class);
        World world = mock(World.class);

        when(versionedZombieService.spawnDemoZombie(player)).thenReturn(zombie);
        when(zombie.getLocation()).thenReturn(new Location(world, 12.0D, 64.0D, -8.0D));

        command.spawn(player);

        verify(versionedZombieService).spawnDemoZombie(player);
        verify(player).sendMessage(ChatColor.GREEN + "Spawned the orbit zombie at 12, 64, -8.");
    }

    @Test
    void spawn_whenTheServiceFails_reportsTheFailureToThePlayer() {
        VersionedZombieService versionedZombieService = mock(VersionedZombieService.class);
        ZombieTestCommand command = new ZombieTestCommand(versionedZombieService);
        Player player = mock(Player.class);

        when(versionedZombieService.spawnDemoZombie(player)).thenThrow(new IllegalStateException("boom"));

        command.spawn(player);

        verify(player).sendMessage(ChatColor.RED + "boom");
    }

    @Test
    void clearSpawn_reportsWhetherTheSpawnedDemoWasRemoved() {
        VersionedZombieService versionedZombieService = mock(VersionedZombieService.class);
        ZombieTestCommand command = new ZombieTestCommand(versionedZombieService);
        Player player = mock(Player.class);

        when(versionedZombieService.clearDemoZombie(player)).thenReturn(true);
        command.clearSpawn(player);
        verify(player).sendMessage(ChatColor.GREEN + "Cleared your spawned demo entity.");

        Player secondPlayer = mock(Player.class);
        when(versionedZombieService.clearDemoZombie(secondPlayer)).thenReturn(false);
        command.clearSpawn(secondPlayer);
        verify(secondPlayer).sendMessage(ChatColor.YELLOW + "You do not have an active spawned demo entity.");
    }

    @Test
    void spawnDynamic_spawnsTheTypedOneOffDemo() {
        VersionedZombieService versionedZombieService = mock(VersionedZombieService.class);
        ZombieTestCommand command = new ZombieTestCommand(versionedZombieService);
        Player player = mock(Player.class);
        Entity entity = mock(Entity.class);
        World world = mock(World.class);

        when(versionedZombieService.spawnDynamicDemoEntity(player, CustomEntityBaseType.COW)).thenReturn(entity);
        when(entity.getLocation()).thenReturn(new Location(world, 1.0D, 70.0D, 2.0D));

        command.spawnDynamic(player, CustomEntityBaseType.COW);

        verify(versionedZombieService).spawnDynamicDemoEntity(player, CustomEntityBaseType.COW);
        verify(player).sendMessage(ChatColor.GREEN + "Spawned a one-off cow demo at 1, 70, 2.");
    }

    @Test
    void attach_reportsTheHookedZombieLocationAndInstructions() {
        VersionedZombieService versionedZombieService = mock(VersionedZombieService.class);
        ZombieTestCommand command = new ZombieTestCommand(versionedZombieService);
        Player player = mock(Player.class);
        Zombie zombie = mock(Zombie.class);
        World world = mock(World.class);

        when(versionedZombieService.attachNearestZombie(player)).thenReturn(zombie);
        when(zombie.getLocation()).thenReturn(new Location(world, 2.0D, 70.0D, 9.0D));

        command.attach(player);

        InOrder inOrder = inOrder(player);
        inOrder.verify(player).sendMessage(ChatColor.GREEN + "Attached the controller demo to the nearest zombie.");
        inOrder.verify(player).sendMessage(ChatColor.YELLOW + "Damage or interact with it, then kill it to trigger the explicit base-on-die demo.");
        inOrder.verify(player).sendMessage(ChatColor.GRAY + "Hooked zombie at 2, 70, 9.");
    }

    @Test
    void clearAttach_reportsWhetherTheAttachedDemoWasRemoved() {
        VersionedZombieService versionedZombieService = mock(VersionedZombieService.class);
        ZombieTestCommand command = new ZombieTestCommand(versionedZombieService);
        Player player = mock(Player.class);

        when(versionedZombieService.clearAttachedZombie(player)).thenReturn(true);
        command.clearAttach(player);
        verify(player).sendMessage(ChatColor.GREEN + "Cleared your attached zombie controller.");

        Player secondPlayer = mock(Player.class);
        when(versionedZombieService.clearAttachedZombie(secondPlayer)).thenReturn(false);
        command.clearAttach(secondPlayer);
        verify(secondPlayer).sendMessage(ChatColor.YELLOW + "You do not have an attached demo zombie.");
    }
}
