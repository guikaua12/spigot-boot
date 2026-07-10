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

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformSchedulers;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ChatUtilsLifecycleTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private ChatConversationManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
        manager = new ChatConversationManager(plugin, PlatformSchedulers.create(plugin));
    }

    @AfterEach
    void tearDown() {
        ChatUtils.uninstall();
        HandlerList.unregisterAll();
        MockBukkit.unmock();
    }

    @Test
    void endToEndCaptureThroughBukkit() {
        server.getPluginManager().registerEvents(manager, plugin);
        ChatUtils.install(manager);
        PlayerMock player = server.addPlayer();

        AtomicReference<String> seen = new AtomicReference<>();
        // .async() so the callback runs inline on the event call, no scheduler tick needed
        ChatUtils.with(player).async().onChat(ctx -> seen.set(ctx.getMessage()));

        AsyncPlayerChatEvent event =
                new AsyncPlayerChatEvent(false, player, "deposit 100", new HashSet<>());
        server.getPluginManager().callEvent(event);

        assertEquals("deposit 100", seen.get());
        assertTrue(event.isCancelled(), "captured chat must be cancelled");
    }

    @Test
    void quitThroughBukkitEndsConversation() {
        server.getPluginManager().registerEvents(manager, plugin);
        ChatUtils.install(manager);
        PlayerMock player = server.addPlayer();

        AtomicReference<EndReason> reason = new AtomicReference<>();
        ChatUtils.with(player).onEnd((p, r) -> reason.set(r)).onChat(ctx -> {});

        server.getPluginManager().callEvent(new PlayerQuitEvent(player, "left"));

        assertEquals(EndReason.DISCONNECT, reason.get());
    }

    @Test
    void onContextReadyInstallsTheFacade() {
        Context context = mock(Context.class);
        manager.onContextReady(context);

        PlayerMock player = server.addPlayer();
        // must not throw now that the facade is installed
        ChatUtils.with(player).onChat(ctx -> {});
    }

    @Test
    void shutdownHookEndsAllWithPluginDisableAndUninstalls() {
        Context context = mock(Context.class);
        AtomicReference<Runnable> hook = new AtomicReference<>();
        doAnswer(inv -> { hook.set(inv.getArgument(0)); return null; })
                .when(context).registerShutdownHook(org.mockito.ArgumentMatchers.any(Runnable.class));

        manager.onContextReady(context);

        PlayerMock a = server.addPlayer();
        PlayerMock b = server.addPlayer();
        AtomicInteger disables = new AtomicInteger();
        ChatUtils.with(a).onEnd((p, r) -> { if (r == EndReason.PLUGIN_DISABLE) disables.incrementAndGet(); }).onChat(ctx -> {});
        ChatUtils.with(b).onEnd((p, r) -> { if (r == EndReason.PLUGIN_DISABLE) disables.incrementAndGet(); }).onChat(ctx -> {});

        hook.get().run(); // simulate context shutdown

        assertEquals(2, disables.get(), "every active conversation ends with PLUGIN_DISABLE");
        assertThrows(IllegalStateException.class, () -> ChatUtils.with(a),
                "the facade is uninstalled after shutdown");
    }
}
