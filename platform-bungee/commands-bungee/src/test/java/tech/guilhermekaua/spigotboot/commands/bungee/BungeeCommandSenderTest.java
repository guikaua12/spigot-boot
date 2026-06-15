/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
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
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeCommandSenderTest {

    @Test
    void playerIdentityIsUuid() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        UUID uuid = UUID.randomUUID();
        when(player.getName()).thenReturn("Alex");
        when(player.getUniqueId()).thenReturn(uuid);

        BungeeCommandSender handle = new BungeeCommandSender(player);

        assertEquals("Alex", handle.getName());
        assertEquals(uuid.toString(), handle.getIdentity());
    }

    @Test
    void consoleIdentityFallsBackToClassAndName() {
        CommandSender console = mock(CommandSender.class);
        when(console.getName()).thenReturn("CONSOLE");

        BungeeCommandSender handle = new BungeeCommandSender(console);

        assertTrue(handle.getIdentity().endsWith(":CONSOLE"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void delegatesPermissionAndMessage() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("a.b")).thenReturn(true);

        BungeeCommandSender handle = new BungeeCommandSender(sender);

        assertTrue(handle.hasPermission("a.b"));
        handle.sendMessage("hi");
        verify(sender).sendMessage("hi");
    }

    @Test
    void unwrapReturnsSenderForAssignableTypeAndEmptyOtherwise() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        BungeeCommandSender handle = new BungeeCommandSender(player);

        Optional<ProxiedPlayer> asPlayer = handle.unwrap(ProxiedPlayer.class);
        Optional<CommandSender> asSender = handle.unwrap(CommandSender.class);
        Optional<String> asString = handle.unwrap(String.class);

        assertSame(player, asPlayer.orElse(null));
        assertSame(player, asSender.orElse(null));
        assertFalse(asString.isPresent());
    }
}
