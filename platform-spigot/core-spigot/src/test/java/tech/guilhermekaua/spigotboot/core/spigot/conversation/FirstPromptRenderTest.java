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
package tech.guilhermekaua.spigotboot.core.spigot.conversation;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.core.spigot.conversation.support.FakeScheduler;
import tech.guilhermekaua.spigotboot.core.spigot.text.HexSupport;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirstPromptRenderTest {

    private ChatConversationManager manager;
    private Player player;
    private Player.Spigot spigot;

    @BeforeEach
    void setUp() {
        org.bukkit.plugin.Plugin plugin = mock(org.bukkit.plugin.Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        manager = new ChatConversationManager(plugin, new FakeScheduler());
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
    }

    @Test
    void firstPromptRendersHexAndColourCodes() {
        // precondition: MockBukkit-v1.20 reports a 1.16+ server, so native hex is on
        assertTrue(HexSupport.NATIVE_HEX, "test server must support native hex for this assertion");

        new ChatPrompt(player, manager)
                .firstPrompt("#ff0000Enter &aamount")
                .onChat(ctx -> {});

        ArgumentCaptor<BaseComponent[]> captor = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(spigot).sendMessage(captor.capture());
        BaseComponent[] sent = captor.getValue();

        String legacy = TextComponent.toLegacyText(sent);
        assertTrue(legacy.contains("Enter"), "visible text must survive: " + legacy);
        assertTrue(legacy.contains("amount"), "visible text must survive: " + legacy);

        // the "#ff0000" token must have flowed through HexSupport into a real red hex colour
        assertEquals(ChatColor.of("#ff0000"), sent[0].getColor(),
                "first run must carry the parsed hex colour");
    }

    @Test
    void blankFirstPromptSendsNothing() {
        new ChatPrompt(player, manager).firstPrompt("   ").onChat(ctx -> {});
        new ChatPrompt(player, manager).onChat(ctx -> {}); // null firstPrompt

        verify(spigot, never()).sendMessage(org.mockito.ArgumentMatchers.<BaseComponent[]>any());
    }
}
