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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.conversation.support.FakeScheduler;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatConversationManagerTest {

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

    @AfterEach
    void tearDown() {
        ChatUtils.uninstall();
    }

    private ChatPrompt prompt() {
        return new ChatPrompt(player, manager);
    }

    @Test
    void dispatchDeliversMessageOnMainThreadByDefault() {
        AtomicReference<String> seen = new AtomicReference<>();
        prompt().onChat(ctx -> seen.set(ctx.getMessage()));

        manager.deliver(player.getUniqueId(), "hello");

        assertEquals("hello", seen.get());
        assertEquals(1, scheduler.runOnEntityCount, "default dispatch must hop through the scheduler");
    }

    @Test
    void asyncDispatchBypassesTheScheduler() {
        AtomicReference<String> seen = new AtomicReference<>();
        prompt().async().onChat(ctx -> seen.set(ctx.getMessage()));

        manager.deliver(player.getUniqueId(), "hi");

        assertEquals("hi", seen.get());
        assertEquals(0, scheduler.runOnEntityCount, "async dispatch must run inline, not via the scheduler");
    }

    @Test
    void callbackWithoutEndStaysActiveForNextMessage() {
        AtomicInteger hits = new AtomicInteger();
        prompt().onChat(ctx -> hits.incrementAndGet()); // never ends

        manager.deliver(player.getUniqueId(), "one");
        manager.deliver(player.getUniqueId(), "two");

        assertEquals(2, hits.get(), "a callback that does not end must keep capturing");
    }

    @Test
    void ctxEndStopsCaptureAndFiresOnEndOnce() {
        AtomicInteger ends = new AtomicInteger();
        AtomicReference<EndReason> reason = new AtomicReference<>();
        prompt()
                .onEnd((p, r) -> { ends.incrementAndGet(); reason.set(r); })
                .onChat(ChatContext::end);

        manager.deliver(player.getUniqueId(), "bye");
        // second message must not reach a callback because the conversation is gone
        manager.deliver(player.getUniqueId(), "again");

        assertEquals(1, ends.get());
        assertEquals(EndReason.ENDED, reason.get());
        assertNull(manager.activeFor(player.getUniqueId()), "ended conversation must be removed");
    }

    @Test
    void startingASecondPromptReplacesTheFirst() {
        AtomicReference<EndReason> firstReason = new AtomicReference<>();
        prompt().onEnd((p, r) -> firstReason.set(r)).onChat(ctx -> {});

        AtomicReference<String> secondSaw = new AtomicReference<>();
        prompt().onChat(ctx -> secondSaw.set(ctx.getMessage()));

        assertEquals(EndReason.REPLACED, firstReason.get(), "the superseded prompt ends with REPLACED");

        manager.deliver(player.getUniqueId(), "routed");
        assertEquals("routed", secondSaw.get(), "messages now reach the new prompt");
    }

    @Test
    void doubleEndIsIdempotent() {
        AtomicInteger ends = new AtomicInteger();
        prompt().onEnd((p, r) -> ends.incrementAndGet()).onChat(ctx -> {
            ctx.end();
            ctx.end();
        });

        manager.deliver(player.getUniqueId(), "x");

        assertEquals(1, ends.get(), "onEnd fires exactly once even if end() is called repeatedly");
    }

    @Test
    void throwingCallbackDoesNotKillTheConversation() {
        AtomicInteger hits = new AtomicInteger();
        prompt().onChat(ctx -> {
            if (hits.incrementAndGet() == 1) {
                throw new RuntimeException("boom");
            }
        });

        manager.deliver(player.getUniqueId(), "first");  // throws, is logged
        manager.deliver(player.getUniqueId(), "second"); // still delivered

        assertEquals(2, hits.get(), "a throwing callback is guarded; the conversation survives");
    }

    @Test
    void withReturnsAPromptBoundToThePlayer() {
        ChatUtils.install(manager);
        AtomicReference<Player> seen = new AtomicReference<>();
        ChatUtils.with(player).onChat(ctx -> seen.set(ctx.getPlayer()));

        manager.deliver(player.getUniqueId(), "hey");
        assertSame(player, seen.get());
    }

    @Test
    void withBeforeInstallThrows() {
        ChatUtils.uninstall();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> ChatUtils.with(player));
    }

    @Test
    void reentrantBeginFromOnEndSkipsStaleSetupForTheDisplacedConversation() {
        // The replaced conversation's onEnd starts a brand-new conversation, displacing the
        // conversation whose begin() is still running. That outer begin() must notice it is no
        // longer the active conversation and skip its own timeout scheduling, leaving only the
        // newest conversation's timeout live (not the displaced, already-ended one's).
        AtomicReference<String> newestSaw = new AtomicReference<>();
        prompt()
                .timeout(30, TimeUnit.SECONDS)
                .onEnd((p, r) -> {
                    if (r == EndReason.REPLACED) {
                        prompt().timeout(30, TimeUnit.SECONDS)
                                .onChat(ctx -> newestSaw.set(ctx.getMessage()));
                    }
                })
                .onChat(ctx -> {});

        // Replaces the first conversation; its onEnd re-enters begin() and starts the newest one.
        prompt().timeout(30, TimeUnit.SECONDS).onChat(ctx -> {});

        assertEquals(1, scheduler.pendingLaterCount(),
                "only the newest conversation may have a live timeout; the displaced begin must not schedule one");

        manager.deliver(player.getUniqueId(), "routed");
        assertEquals("routed", newestSaw.get(), "messages reach the newest conversation, not a stale one");
    }
}
