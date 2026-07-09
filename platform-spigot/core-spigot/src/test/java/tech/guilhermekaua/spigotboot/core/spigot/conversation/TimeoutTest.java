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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimeoutTest {

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

    @Test
    void timeoutFiresOnTimeoutThenOnEnd() {
        AtomicInteger order = new AtomicInteger();
        AtomicReference<Integer> timeoutOrder = new AtomicReference<>();
        AtomicReference<Integer> endOrder = new AtomicReference<>();
        AtomicReference<EndReason> endReason = new AtomicReference<>();

        new ChatPrompt(player, manager)
                .timeout(30, TimeUnit.SECONDS)
                .onTimeout(p -> timeoutOrder.set(order.incrementAndGet()))
                .onEnd((p, r) -> { endOrder.set(order.incrementAndGet()); endReason.set(r); })
                .onChat(ctx -> {});

        scheduler.fireAllLater(); // simulate the timeout elapsing

        assertEquals(1, timeoutOrder.get(), "onTimeout fires first");
        assertEquals(2, endOrder.get(), "onEnd fires second");
        assertEquals(EndReason.TIMEOUT, endReason.get());
        assertNull(manager.activeFor(player.getUniqueId()), "timed-out conversation is removed");
    }

    @Test
    void aMessageResetsTheTimeoutWindow() {
        AtomicInteger timeouts = new AtomicInteger();
        new ChatPrompt(player, manager)
                .timeout(30, TimeUnit.SECONDS)
                .onTimeout(p -> timeouts.incrementAndGet())
                .onChat(ctx -> {}); // never ends

        manager.deliver(player.getUniqueId(), "still here"); // resets: old timeout task is stale

        assertEquals(1, scheduler.pendingLaterCount(),
                "the superseded timeout task must be cancelled, leaving one live task");

        scheduler.fireAllLater(); // fires both the stale (cancelled) and the fresh task
        assertEquals(1, timeouts.get(), "only the fresh timeout may fire, exactly once");
    }

    @Test
    void noTimeoutWhenNotConfigured() {
        new ChatPrompt(player, manager).onChat(ctx -> {});
        assertEquals(0, scheduler.pendingLaterCount(), "no timeout scheduled when timeout() is not called");
    }

    @Test
    void negativeTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChatPrompt(player, manager).timeout(-1, TimeUnit.SECONDS),
                "a negative duration must not silently disable the timeout");
    }

    @Test
    void zeroTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChatPrompt(player, manager).timeout(0, TimeUnit.SECONDS),
                "a zero duration must not silently disable the timeout");
    }

    @Test
    void subMillisecondTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChatPrompt(player, manager).timeout(500, TimeUnit.NANOSECONDS),
                "a positive duration that truncates to 0ms must not silently disable the timeout");
    }
}
