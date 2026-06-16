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
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeCommandPlatformSupportTest {
    private final BungeeCommandPlatformSupport support = new BungeeCommandPlatformSupport();

    @Test
    void createSenderWrapsBungeeSender() {
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("Alex");

        CommandSenderHandle handle = support.createSender(player);

        assertEquals("Alex", handle.getName());
        assertSame(player, handle.unwrap(ProxiedPlayer.class).orElse(null));
    }

    @Test
    void createSenderRejectsNonBungeeSender() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> support.createSender("not a sender"));
        assertTrue(ex.getMessage().contains("Expected a BungeeCord CommandSender"));
    }

    @Test
    void isSenderTypeMatchesCommandSenderHierarchy() {
        assertTrue(support.isSenderType(CommandSender.class));
        assertTrue(support.isSenderType(ProxiedPlayer.class));
        assertFalse(support.isSenderType(String.class));
        assertFalse(support.isSenderType(null));
    }
}
