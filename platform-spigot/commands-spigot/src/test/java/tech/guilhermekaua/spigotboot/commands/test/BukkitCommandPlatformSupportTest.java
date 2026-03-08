package tech.guilhermekaua.spigotboot.commands.test;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandPlatformSupport;

import static org.junit.jupiter.api.Assertions.*;

class BukkitCommandPlatformSupportTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createSenderWrapsBukkitSenderAndUnwrapsSpecificTypes() {
        Player player = server.addPlayer("Alex");
        BukkitCommandPlatformSupport platformSupport = new BukkitCommandPlatformSupport();

        CommandSenderHandle sender = platformSupport.createSender(player);

        assertEquals("Alex", sender.getName());
        assertEquals(player.getUniqueId().toString(), sender.getIdentity());
        assertSame(player, sender.unwrap(Player.class).orElse(null));
        assertSame(player, sender.unwrap(CommandSender.class).orElse(null));
        assertFalse(sender.unwrap(String.class).isPresent());
    }

    @Test
    void senderTypeDetectionMatchesBukkitSenderHierarchy() {
        BukkitCommandPlatformSupport platformSupport = new BukkitCommandPlatformSupport();

        assertTrue(platformSupport.isSenderType(CommandSender.class));
        assertTrue(platformSupport.isSenderType(Player.class));
        assertFalse(platformSupport.isSenderType(String.class));
    }

    @Test
    void createSenderRejectsNonBukkitSender() {
        BukkitCommandPlatformSupport platformSupport = new BukkitCommandPlatformSupport();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> platformSupport.createSender("nope")
        );

        assertTrue(exception.getMessage().contains("Expected a Bukkit CommandSender"));
    }
}
