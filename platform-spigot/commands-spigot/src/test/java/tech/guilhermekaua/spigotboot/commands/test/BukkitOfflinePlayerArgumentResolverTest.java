package tech.guilhermekaua.spigotboot.commands.test;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.BukkitOfflinePlayerArgumentResolver;

import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.SpigotCommandMessages;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BukkitOfflinePlayerArgumentResolverTest {
    private final BukkitOfflinePlayerArgumentResolver resolver = new BukkitOfflinePlayerArgumentResolver();
    private final CommandExecutionContext context = mock(CommandExecutionContext.class);
    private final CommandParameterMetadata parameter = new CommandParameterMetadata(
            null,
            0,
            "target",
            "target",
            OfflinePlayer.class,
            OfflinePlayer.class,
            false,
            false,
            false,
            null,
            null
    );

    @Test
    void resolveReturnsOnlinePlayerWithoutScanningOfflinePlayers() {
        Player onlinePlayer = mock(Player.class);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(onlinePlayer);

            OfflinePlayer resolved = resolver.resolve(context, parameter, "Alex");

            assertSame(onlinePlayer, resolved);
            bukkit.verify(() -> Bukkit.getPlayerExact("Alex"));
            bukkit.verify(Bukkit::getOfflinePlayers, never());
            bukkit.verify(() -> Bukkit.getOfflinePlayer("Alex"), never());
        }
    }

    @Test
    void resolveFallsBackToDirectOfflineLookupWithoutScanningOfflinePlayers() {
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        when(offlinePlayer.getName()).thenReturn("Alex");

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(null);
            bukkit.when(() -> Bukkit.getOfflinePlayer("Alex")).thenReturn(offlinePlayer);

            OfflinePlayer resolved = resolver.resolve(context, parameter, "Alex");

            assertSame(offlinePlayer, resolved);
            bukkit.verify(() -> Bukkit.getPlayerExact("Alex"));
            bukkit.verify(() -> Bukkit.getOfflinePlayer("Alex"));
            bukkit.verify(Bukkit::getOfflinePlayers, never());
        }
    }

    @Test
    void resolveRejectsUnknownOfflinePlayerAfterDirectLookup() {
        OfflinePlayer missingPlayer = mock(OfflinePlayer.class);
        when(missingPlayer.getName()).thenReturn(null);
        when(missingPlayer.hasPlayedBefore()).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Missing")).thenReturn(null);
            bukkit.when(() -> Bukkit.getOfflinePlayer("Missing")).thenReturn(missingPlayer);

            CommandMessageException exception = assertThrows(
                    CommandMessageException.class,
                    () -> resolver.resolve(context, parameter, "Missing")
            );
            assertSame(SpigotCommandMessages.OFFLINE_NOT_FOUND, exception.getKey());

            bukkit.verify(() -> Bukkit.getPlayerExact("Missing"));
            bukkit.verify(() -> Bukkit.getOfflinePlayer("Missing"));
            bukkit.verify(Bukkit::getOfflinePlayers, never());
        }
    }
}
