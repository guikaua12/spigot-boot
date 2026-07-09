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

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.conversation.support.FakeScheduler;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatContextCaptureTest {

    private FakeScheduler scheduler;
    private ChatConversationManager manager;
    private Player player;

    @BeforeEach
    void setUp() {
        scheduler = new FakeScheduler();
        org.bukkit.plugin.Plugin plugin = mock(org.bukkit.plugin.Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        manager = new ChatConversationManager(plugin, scheduler);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    private AsyncPlayerChatEvent chat(Player who, String message) {
        return new AsyncPlayerChatEvent(false, who, message, new HashSet<>());
    }

    @Test
    void participantMessageIsCapturedAndCancelled() {
        AtomicReference<String> seen = new AtomicReference<>();
        new ChatPrompt(player, manager).onChat(ctx -> seen.set(ctx.getMessage()));

        AsyncPlayerChatEvent event = chat(player, "42");
        manager.onChat(event);

        assertEquals("42", seen.get());
        assertTrue(event.isCancelled(), "a captured message must not broadcast to public chat");
    }

    @Test
    void nonParticipantMessagePassesThrough() {
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());

        AtomicInteger hits = new AtomicInteger();
        new ChatPrompt(player, manager).onChat(ctx -> hits.incrementAndGet());

        AsyncPlayerChatEvent event = chat(other, "hello world");
        manager.onChat(event);

        assertEquals(0, hits.get(), "a player without a conversation is not captured");
        assertFalse(event.isCancelled(), "normal chat must not be cancelled");
    }

    @Test
    void repromptCapturesTheSecondMessage() {
        AtomicInteger hits = new AtomicInteger();
        new ChatPrompt(player, manager).onChat(ctx -> hits.incrementAndGet()); // never ends

        manager.onChat(chat(player, "one"));
        manager.onChat(chat(player, "two"));

        assertEquals(2, hits.get());
    }

    @Test
    void afterEndMessageIsNotCaptured() {
        new ChatPrompt(player, manager).onChat(ChatContext::end);

        manager.onChat(chat(player, "done"));
        AsyncPlayerChatEvent afterEnd = chat(player, "late");
        manager.onChat(afterEnd);

        assertFalse(afterEnd.isCancelled(), "once ended, chat flows normally again");
        assertNull(manager.activeFor(player.getUniqueId()));
    }

    @Test
    void quitEndsConversationWithDisconnect() {
        AtomicReference<EndReason> reason = new AtomicReference<>();
        new ChatPrompt(player, manager).onEnd((p, r) -> reason.set(r)).onChat(ctx -> {});

        when(player.getName()).thenReturn("Steve");
        manager.onQuit(new PlayerQuitEvent(player, "left"));

        assertEquals(EndReason.DISCONNECT, reason.get());
        assertNull(manager.activeFor(player.getUniqueId()));
    }
}
