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
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewEngineOpenOrderingTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;
    private List<String> log;
    private ViewB viewB;

    static final class ViewA extends View {
        private final List<String> log;

        ViewA(List<String> log) {
            this.log = log;
        }

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("A").rows(1);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            log.add("A.onClose(" + context.reason() + ")");
        }
    }

    static final class ViewB extends View {
        private final List<String> log;
        boolean cancelNext;
        boolean throwNext;

        ViewB(List<String> log) {
            this.log = log;
        }

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("B").rows(1);
        }

        @Override
        protected void onOpen(@NotNull OpenContext context) {
            log.add("B.onOpen");
            if (throwNext) {
                throw new IllegalStateException("boom");
            }
            if (cancelNext) {
                context.cancelOpen();
            }
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            log.add("B.onFirstRender");
        }
    }

    static final class InitialStateView extends View {
        @SuppressWarnings("unused")
        private final MutableState<Integer> count = initialState("count", Integer.class);

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("C").rows(1);
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        log = new ArrayList<>();
        viewB = new ViewB(log);
        views.register(new ViewA(log));
        views.register(viewB);
        views.register(new InitialStateView());
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession openViewA() {
        engine.open(player, ViewA.class, ViewArguments.empty());
        return sessions.find(player.getUniqueId()).orElseThrow();
    }

    private InventoryCloseEvent closeEventFor(Inventory inventory) {
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inventory);
        return new InventoryCloseEvent(view);
    }

    @Test
    void cancelOpen_leavesPreviousSessionActive() {
        ViewSession previous = openViewA();
        viewB.cancelNext = true;

        engine.open(player, ViewB.class, ViewArguments.empty());

        assertEquals(ViewSession.Status.ACTIVE, previous.status());
        assertSame(previous, sessions.find(player.getUniqueId()).orElseThrow());
        assertSame(previous.inventory(), player.getOpenInventory().getTopInventory());
        assertEquals(Collections.singletonList("B.onOpen"), log,
                "cancelled open must not close the previous view nor reach onFirstRender");
    }

    @Test
    void onOpenThrows_leavesPreviousSessionActive() {
        ViewSession previous = openViewA();
        viewB.throwNext = true;

        engine.open(player, ViewB.class, ViewArguments.empty());

        assertEquals(ViewSession.Status.ACTIVE, previous.status());
        assertSame(previous, sessions.find(player.getUniqueId()).orElseThrow());
        assertSame(previous.inventory(), player.getOpenInventory().getTopInventory());
        assertEquals(Collections.singletonList("B.onOpen"), log);
    }

    @Test
    void committedOpen_closesPreviousWithReplaced_beforeNewContainerShown() {
        openViewA();

        engine.open(player, ViewB.class, ViewArguments.empty());

        assertEquals(Arrays.asList("B.onOpen", "A.onClose(REPLACED)", "B.onFirstRender"), log);
        ViewSession current = sessions.find(player.getUniqueId()).orElseThrow();
        assertSame(viewB, current.registered().instance());
        assertEquals(ViewSession.Status.ACTIVE, current.status());
        assertSame(current.inventory(), player.getOpenInventory().getTopInventory());
    }

    @Test
    void initialState_wrongTypedArgument_throwsAtOpenSite_previousIntact() {
        ViewSession previous = openViewA();

        assertThrows(IllegalArgumentException.class, () -> engine.open(player,
                InitialStateView.class, ViewArguments.of("count", "not-an-int")));

        assertEquals(ViewSession.Status.ACTIVE, previous.status());
        assertSame(previous, sessions.find(player.getUniqueId()).orElseThrow());
        assertTrue(log.isEmpty(), "the previous view must not observe a failed open");
    }

    @Test
    void staleCloseEvent_forDifferentContainer_isIgnored() {
        ViewSession session = openViewA();
        Inventory unrelated = Bukkit.createInventory(null, 9);

        engine.bukkitClose(session, closeEventFor(unrelated));

        assertEquals(ViewSession.Status.ACTIVE, session.status());
        assertSame(session, sessions.find(player.getUniqueId()).orElseThrow());
        assertTrue(log.isEmpty());
    }

    @Test
    void closeEvent_forOwnContainer_closesWithPlayerReason() {
        ViewSession session = openViewA();

        engine.bukkitClose(session, closeEventFor(session.inventory()));

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(Collections.singletonList("A.onClose(PLAYER)"), log);
    }
}
