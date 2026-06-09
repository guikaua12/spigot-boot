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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.listener.ViewListener;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.when;

class NavigationPairFlowTest {

    private ServerMock server;
    private MockPlugin plugin;
    private PlayerMock player;
    private ViewService service;
    private ViewListener listener;
    private SessionRegistry sessions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        views.register(new ShopFlowView());
        views.register(new ConfirmFlowView());
        ViewEngine engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
        service = new ViewService(engine, sessions);
        listener = new ViewListener(sessions, engine);
    }

    @AfterEach
    void tearDown() {
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    @Test
    void buyButton_opensConfirmWithTheSelectedItem_thenBackReturnsToShop() {
        service.open(player, ShopFlowView.class);
        Inventory shop = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        assertEquals(Material.DIAMOND, shop.getItem(0).getType());

        // click the diamond "buy" button → deferred openView(ConfirmFlowView, {item:DIAMOND})
        listener.onClick(click(shop, 0));
        server.getScheduler().performTicks(1);

        Inventory confirm = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        // the confirm view rendered the passed selection (its centre slot (4) shows the chosen material)
        assertNotNull(confirm.getItem(4));
        assertEquals(Material.DIAMOND, confirm.getItem(4).getType());

        // click the "back" button (slot 0) → deferred openView(ShopFlowView)
        listener.onClick(click(confirm, 0));
        server.getScheduler().performTicks(1);

        Inventory backToShop = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        // back on the shop: its diamond offer is rendered again, and the confirm container is gone
        assertEquals(Material.DIAMOND, backToShop.getItem(0).getType());
        assertNotSame(confirm, backToShop, "navigating back replaced the confirm container with a fresh shop");
    }

    private InventoryClickEvent click(Inventory top, int slot) {
        InventoryView view = Mockito.mock(InventoryView.class);
        Inventory bottom = player.getInventory();
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);
        when(view.getPlayer()).thenReturn(player);
        when(view.convertSlot(Mockito.anyInt())).thenAnswer(i -> i.getArgument(0));
        when(view.getInventory(Mockito.anyInt())).thenAnswer(i -> {
            int raw = i.getArgument(0);
            if (raw < 0) {
                return null;
            }
            return raw < top.getSize() ? top : bottom;
        });
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    // sample-shaped views (mirror ShopView/ConfirmView in test-plugin) ----------------

    public static final class ShopFlowView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Shop").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.DIAMOND))
                    .openOnClick(ConfirmFlowView.class,
                            ViewArguments.of("item", Material.DIAMOND.name()));
        }
    }

    public static final class ConfirmFlowView extends View {
        private final MutableState<String> item = initialState("item", String.class);

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Confirm").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.BARRIER))
                    .openOnClick(ShopFlowView.class);
            render.slot(4).item(ctx -> new ItemStack(Material.valueOf(item.get(ctx))));
        }
    }
}
