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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedStateFlowTest {

    private ServerMock server;
    private MockPlugin plugin;
    private ViewService service;
    private SessionRegistry sessions;
    private LeaderboardFlowView leaderboard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        leaderboard = new LeaderboardFlowView();
        views.register(leaderboard);
        ViewEngine engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
        service = new ViewService(engine, sessions);
    }

    @AfterEach
    void tearDown() {
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    @Test
    void sharedStateSet_repaintsTheWatchingComponentForEveryOpenSession() {
        PlayerMock a = server.addPlayer("a");
        PlayerMock b = server.addPlayer("b");
        service.open(a, LeaderboardFlowView.class);
        service.open(b, LeaderboardFlowView.class);

        Inventory invA = sessions.find(a.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        Inventory invB = sessions.find(b.getUniqueId()).orElseThrow(IllegalStateException::new).inventory();
        // amount encodes the name length so we can observe repaints deterministically
        assertEquals("nobody".length(), invA.getItem(0).getAmount());
        assertEquals("nobody".length(), invB.getItem(0).getAmount());

        // a single shared write must repaint the watching component of BOTH open sessions
        leaderboard.topName.set("champion");

        assertEquals("champion".length(), invA.getItem(0).getAmount());
        assertEquals("champion".length(), invB.getItem(0).getAmount());
    }

    static final class LeaderboardFlowView extends View {
        final SharedState<String> topName = sharedState("nobody");

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Leaderboard").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> new ItemStack(Material.PAPER, Math.max(1, topName.get().length())))
                    .updateOnStateChange(topName);
        }
    }
}
