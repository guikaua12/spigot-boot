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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewListenerTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private ViewListener listener;
    private PlayerMock player;
    private PlayerMock second;
    private ListenerView listenerView;

    static final class ListenerView extends View {
        int clicks;
        final List<CloseReason> closeReasons = new ArrayList<>();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Listener").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .onClick(ctx -> clicks++);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            closeReasons.add(context.reason());
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        second = server.addPlayer("second");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        listenerView = new ListenerView();
        views.register(listenerView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        });
        listener = new ViewListener(sessions, engine);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession sessionOf(PlayerMock who) {
        return sessions.find(who.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private InventoryView mockView(PlayerMock who, Inventory top) {
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(who.getInventory());
        when(invView.getPlayer()).thenReturn(who);
        when(invView.convertSlot(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        return invView;
    }

    private InventoryClickEvent clickFor(PlayerMock who, Inventory top, int rawSlot) {
        return new InventoryClickEvent(mockView(who, top), InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private InventoryDragEvent dragFor(PlayerMock who, Inventory top, int rawSlot) {
        Map<Integer, ItemStack> slots = new HashMap<>();
        slots.put(rawSlot, new ItemStack(Material.STONE));
        return new InventoryDragEvent(mockView(who, top), null,
                new ItemStack(Material.STONE), false, slots);
    }

    private InventoryCloseEvent closeEventFor(PlayerMock who, Inventory top) {
        InventoryView invView = mock(InventoryView.class);
        when(invView.getPlayer()).thenReturn(who);
        when(invView.getTopInventory()).thenReturn(top);
        return new InventoryCloseEvent(invView);
    }

    @Test
    void click_withSession_delegatesToEngine() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        InventoryClickEvent event = clickFor(player, sessionOf(player).inventory(), 0);

        listener.onClick(event);

        assertEquals(1, listenerView.clicks);
        assertTrue(event.isCancelled());
    }

    @Test
    void click_withoutSession_isIgnored() {
        InventoryClickEvent event = clickFor(player, Bukkit.createInventory(null, 9), 0);

        listener.onClick(event);

        assertEquals(0, listenerView.clicks);
        assertFalse(event.isCancelled());
    }

    @Test
    void drag_withSession_appliesDragPolicy() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        InventoryDragEvent event = dragFor(player, sessionOf(player).inventory(), 0);

        listener.onDrag(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void drag_withoutSession_isIgnored() {
        InventoryDragEvent event = dragFor(player, Bukkit.createInventory(null, 9), 0);

        listener.onDrag(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void close_forOwnContainer_closesWithPlayerReason() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);

        listener.onClose(closeEventFor(player, session.inventory()));

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(Arrays.asList(CloseReason.PLAYER), listenerView.closeReasons);
    }

    @Test
    void close_forDifferentPlayersInventory_doesNothing() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);

        // the close event belongs to another player without a session, over the same container
        listener.onClose(closeEventFor(second, session.inventory()));

        assertEquals(ViewSession.Status.ACTIVE, session.status());
        assertTrue(listenerView.closeReasons.isEmpty());
    }

    @Test
    void quit_withSession_closesWithDisconnect() {
        engine.open(player, ListenerView.class, ViewArguments.empty());

        listener.onQuit(new PlayerQuitEvent(player, "bye"));

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(Arrays.asList(CloseReason.DISCONNECT), listenerView.closeReasons);
    }

    @Test
    void quit_withoutSession_isIgnored() {
        assertDoesNotThrow(() -> listener.onQuit(new PlayerQuitEvent(player, "bye")));

        assertTrue(listenerView.closeReasons.isEmpty());
    }

    @Test
    void pluginDisable_closesEverySessionOfTheEnginePlugin() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        engine.open(second, ListenerView.class, ViewArguments.empty());

        listener.onPluginDisable(new PluginDisableEvent(engine.plugin()));

        assertTrue(sessions.all().isEmpty());
        assertEquals(Arrays.asList(CloseReason.PLUGIN_DISABLE, CloseReason.PLUGIN_DISABLE),
                listenerView.closeReasons);
    }

    @Test
    void pluginDisable_ofAnotherPlugin_isIgnored() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        Plugin other = MockBukkit.createMockPlugin("other");

        listener.onPluginDisable(new PluginDisableEvent(other));

        assertEquals(1, sessions.all().size());
        assertTrue(listenerView.closeReasons.isEmpty());
    }
}
