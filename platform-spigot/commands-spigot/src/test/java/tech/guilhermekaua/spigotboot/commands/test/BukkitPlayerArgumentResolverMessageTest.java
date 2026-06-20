package tech.guilhermekaua.spigotboot.commands.test;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.BukkitPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.SpigotCommandMessages;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BukkitPlayerArgumentResolverMessageTest {
    private final BukkitPlayerArgumentResolver resolver = new BukkitPlayerArgumentResolver();
    private final CommandExecutionContext context = mock(CommandExecutionContext.class);
    private final CommandParameterMetadata parameter = new CommandParameterMetadata(
            null, 0, "target", "target", Player.class, Player.class, false, false, false, null, null);

    @Test
    void unknownPlayerThrowsNotFoundKey() {
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("ghost")).thenReturn(null);
            bukkit.when(() -> Bukkit.matchPlayer("ghost")).thenReturn(Collections.emptyList());

            CommandMessageException exception = assertThrows(
                    CommandMessageException.class,
                    () -> resolver.resolve(context, parameter, "ghost"));

            assertSame(SpigotCommandMessages.PLAYER_NOT_FOUND, exception.getKey());
            assertEquals("ghost", exception.getPlaceholders().get("input"));
        }
    }
}
