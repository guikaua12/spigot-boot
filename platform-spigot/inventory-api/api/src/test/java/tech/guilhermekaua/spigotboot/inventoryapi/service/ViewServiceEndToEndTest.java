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
package tech.guilhermekaua.spigotboot.inventoryapi.service;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.BukkitPlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.ViewDiscoveryService;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.listener.ViewListener;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewServiceEndToEndTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private ViewService service;
    private ViewListener listener;
    private SampleView view;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        ViewRegistry registry = new ViewRegistry(new ViewDiscoveryService());
        sessions = new SessionRegistry();
        engine = new ViewEngine(plugin, registry, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
                }, new BukkitPlatformScheduler(plugin));
        service = new ViewService(engine, sessions);
        listener = new ViewListener(sessions, engine);
        view = new SampleView();
        registry.register(view);
        player = server.addPlayer("tester");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private InventoryClickEvent click(int rawSlot) {
        Inventory top = session().inventory();
        Inventory bottom = player.getInventory();
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(bottom);
        when(invView.getPlayer()).thenReturn(player);
        when(invView.convertSlot(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(invView.getInventory(anyInt())).thenAnswer(inv -> {
            int raw = inv.getArgument(0);
            if (raw < 0) {
                return null;
            }
            return raw < top.getSize() ? top : bottom;
        });
        return new InventoryClickEvent(invView, InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private static void assertSlotEmpty(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        assertTrue(item == null || item.getType() == Material.AIR, "slot " + slot + " should be empty");
    }

    @Test
    void fullFlowOpenClickReactRepaintAndDisconnectClose() {
        service.open(player, SampleView.class);
        ViewSession session = session();
        assertEquals(ViewSession.Status.ACTIVE, session.status());
        Inventory inventory = session.inventory();

        // initial paint: layout row 2 is '<  AAA  >'
        assertEquals(Material.STONE, inventory.getItem(12).getType());
        assertSlotEmpty(inventory, 9);
        assertEquals(Material.PAPER, inventory.getItem(17).getType());
        assertEquals(1, inventory.getItem(17).getAmount());

        // click protection + reactive repaint on increment
        InventoryClickEvent increment = click(12);
        listener.onClick(increment);
        assertTrue(increment.isCancelled(), "top clicks are deny-by-default");
        assertEquals(Material.ARROW, inventory.getItem(9).getType(), "back arrow appears once counter > 0");
        assertEquals(2, inventory.getItem(17).getAmount());

        // decrement via the nav-like component hides it again
        listener.onClick(click(9));
        assertSlotEmpty(inventory, 9);
        assertEquals(1, inventory.getItem(17).getAmount());

        // live context
        Optional<ViewContext> context = service.contextOf(player);
        assertTrue(context.isPresent());
        assertSame(view, context.get().view());
        assertTrue(context.get().isActive());

        // disconnect close
        listener.onQuit(new PlayerQuitEvent(player, "bye"));
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(CloseReason.DISCONNECT, view.lastCloseReason);
        assertFalse(service.contextOf(player).isPresent());
    }

    @Test
    void apiCloseClosesSession() {
        service.open(player, SampleView.class);

        service.close(player);

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(CloseReason.API, view.lastCloseReason);
    }

    @Test
    void closeWithoutSessionIsNoOp() {
        service.close(player);

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void openUnknownViewThrows() {
        assertThrows(UnknownViewException.class, () -> service.open(player, UnregisteredView.class));
    }

    @Test
    void openOffMainThreadThrows() throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                service.open(player, SampleView.class);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        thread.start();
        thread.join();

        assertTrue(thrown.get() instanceof IllegalStateException,
                "ViewService.open must assert the main thread");
    }

    public static final class SampleView extends View {
        final MutableState<Integer> counter = mutableState(0);
        CloseReason lastCloseReason;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aSample")
                    .layout("         ",
                            "<  AAA  >",
                            "         ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.layoutSlot('A', new ItemStack(Material.STONE))
                    .onClick(ctx -> counter.update(ctx, v -> v + 1));
            render.layoutSlot('<', new ItemStack(Material.ARROW))
                    .displayIf(ctx -> counter.get(ctx) > 0)
                    .updateOnStateChange(counter)
                    .onClick(ctx -> counter.update(ctx, v -> v > 0 ? v - 1 : 0));
            render.layoutSlot('>')
                    .item(ctx -> new ItemStack(Material.PAPER, counter.get(ctx) + 1))
                    .updateOnStateChange(counter);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    public static final class UnregisteredView extends View {
    }
}
