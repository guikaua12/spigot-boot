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
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
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
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.FirstRenderPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationFrameSlotFlowsTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;

    private final ComponentConflictView componentConflictView = new ComponentConflictView();
    private final TwoPaginationConflictView twoPaginationConflictView = new TwoPaginationConflictView();
    private final EmptyStateView emptyStateView = new EmptyStateView();
    private final AsyncEmptyStateView asyncEmptyStateView = new AsyncEmptyStateView();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        views.register(componentConflictView);
        views.register(twoPaginationConflictView);
        views.register(emptyStateView);
        views.register(asyncEmptyStateView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        }, new BukkitPlatformScheduler(plugin));
    }

    @AfterEach
    void tearDown() {
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- fixture views

    static final class ComponentConflictView extends View {
        CloseReason lastCloseReason;
        final Pagination<String> pagination = this.<String>paginate(Collections.<String>emptyList())
                .layout(Layout.ofSlots(0, 1))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 8)
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Conflict").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(8, new ItemStack(Material.STONE)); // collides with the empty-state frame slot
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class TwoPaginationConflictView extends View {
        CloseReason lastCloseReason;
        final Pagination<String> a = this.<String>paginate(Collections.<String>emptyList())
                .layout(Layout.ofSlots(0, 1))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 4) // 4 is pagination b's target
                .build();
        final Pagination<String> b = this.<String>paginate(Collections.singletonList("x"))
                .layout(Layout.ofSlots(3, 4))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("TwoPag").rows(1);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class EmptyStateView extends View {
        final Pagination<String> pagination = this.<String>paginate(Collections.<String>emptyList())
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 4)
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("EmptyState").layout("OOOOOOOOO");
        }
    }

    static final class AsyncEmptyStateView extends View {
        final Map<UUID, CompletableFuture<PageResult<String>>> futures = new ConcurrentHashMap<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            CompletableFuture<PageResult<String>> future = new CompletableFuture<>();
            futures.put(request.playerId(), future);
            return future;
        })
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .loadingItem(ctx -> new ItemStack(Material.EMERALD), 4)
                .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 4)
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("AsyncEmpty").layout("OOOOOOOOO");
        }
    }

    // ---------------------------------------------------------------- tests

    @Test
    void emptyStateSlotOnComponent_abortsOpenFailed() {
        Logger logger = Logger.getLogger(FirstRenderPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, ComponentConflictView.class, ViewArguments.empty());

            assertFalse(sessions.find(player.getUniqueId()).isPresent(),
                    "the overlap must abort the open, registering nothing");
            assertEquals(CloseReason.OPEN_FAILED, componentConflictView.lastCloseReason);
            assertTrue(handler.hasThrownContaining("a pagination frame item"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void frameSlotOnAnotherPagination_abortsOpenFailed() {
        Logger logger = Logger.getLogger(FirstRenderPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, TwoPaginationConflictView.class, ViewArguments.empty());

            assertFalse(sessions.find(player.getUniqueId()).isPresent());
            assertEquals(CloseReason.OPEN_FAILED, twoPaginationConflictView.lastCloseReason);
            assertTrue(handler.hasThrownContaining("two paginations"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void eagerEmptySource_paintsEmptyStateOnOpen() {
        engine.open(player, EmptyStateView.class, ViewArguments.empty());

        ViewSession session = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        Inventory inventory = session.inventory();
        assertEquals(Material.BARRIER, inventory.getItem(4).getType());
        assertNull(inventory.getItem(0), "the rest of the layout is empty");
        assertNull(inventory.getItem(8));
    }

    @Test
    void async_showsLoadingSlot_thenEmptyStateAfterSettlingEmpty() {
        engine.open(player, AsyncEmptyStateView.class, ViewArguments.empty());

        ViewSession session = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        Inventory inventory = session.inventory();
        // in flight: the loading slot shows the loading item, the rest of the layout is empty
        assertEquals(Material.EMERALD, inventory.getItem(4).getType());
        assertNull(inventory.getItem(0));

        // settle empty on the main thread: the inline settle repaints
        asyncEmptyStateView.futures.get(player.getUniqueId())
                .complete(PageResult.of(Collections.<String>emptyList(), 0));

        assertEquals(Material.BARRIER, inventory.getItem(4).getType(),
                "settled-empty replaces the loading frame with the empty-state frame");
        assertNull(inventory.getItem(0));
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        boolean hasThrownContaining(String fragment) {
            for (LogRecord record : records) {
                Throwable thrown = record.getThrown();
                if (thrown != null && thrown.getMessage() != null
                        && thrown.getMessage().contains(fragment)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
