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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateFlushTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;
    private PlayerMock second;

    private ReactiveView reactiveView;
    private CascadeView cascadeView;
    private FeedbackView feedbackView;
    private ScheduledView scheduledView;
    private SharedView sharedView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        reactiveView = new ReactiveView();
        cascadeView = new CascadeView();
        feedbackView = new FeedbackView();
        scheduledView = new ScheduledView();
        sharedView = new SharedView();
        views.register(reactiveView);
        views.register(cascadeView);
        views.register(feedbackView);
        views.register(scheduledView);
        views.register(sharedView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        });
        player = server.addPlayer("first");
        second = server.addPlayer("second");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession sessionOf(PlayerMock who) {
        return sessions.find(who.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private InventoryClickEvent click(PlayerMock who, ViewSession session, int rawSlot) {
        Inventory top = session.inventory();
        Inventory bottom = who.getInventory();
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(bottom);
        when(invView.getPlayer()).thenReturn(who);
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

    @Test
    void stateChange_insideClickHandler_repaintsOnlyWatchingComponent() {
        engine.open(player, ReactiveView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);
        Inventory inventory = session.inventory();

        // initial paint runs every renderer exactly once
        assertEquals(Material.EMERALD, inventory.getItem(0).getType());
        assertEquals(1, reactiveView.watchedRenders.get());
        assertEquals(1, reactiveView.unwatchedRenders.get());
        assertEquals(1, inventory.getItem(1).getAmount());

        engine.click(session, click(player, session, 2));

        assertEquals(Material.DIAMOND, inventory.getItem(0).getType(),
                "watching component must repaint with the new state");
        assertEquals(2, reactiveView.watchedRenders.get());
        assertEquals(1, reactiveView.unwatchedRenders.get(),
                "unwatched component must not re-render on a state flush");
        assertEquals(1, inventory.getItem(1).getAmount(),
                "unwatched slot must keep its previous content");
    }

    @Test
    void stateChange_duringOnUpdate_cascadesExactlyOneExtraPass() {
        engine.open(player, CascadeView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);
        assertEquals(1, cascadeView.renders.get());
        assertEquals(0, cascadeView.updates.get());

        engine.update(session, UpdateTrigger.EXPLICIT);

        // explicit pass + exactly one cascaded STATE_CHANGE mini-pass
        assertEquals(2, cascadeView.updates.get());
        assertEquals(3, cascadeView.renders.get());
        assertFalse(session.stateStore().hasDirty());
    }

    @Test
    void selfFeedingRenderer_stopsAtCascadeCapWithWarning() {
        Logger logger = Logger.getLogger(ViewEngine.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, FeedbackView.class, ViewArguments.empty());
            ViewSession session = sessionOf(player);
            assertEquals(1, feedbackView.renders.get());

            engine.update(session, UpdateTrigger.EXPLICIT);

            // one explicit pass plus eight capped cascade passes, then stop
            assertEquals(10, feedbackView.renders.get());
            assertFalse(session.stateStore().hasDirty(),
                    "remaining dirty tokens must be dropped at the cap");
            assertTrue(handler.hasWarningContaining("feedback loop"),
                    "hitting the cascade cap must log a WARNING");

            // the engine stays responsive after the cap
            server.getScheduler().performTicks(1);
            assertEquals(ViewSession.Status.ACTIVE, session.status());
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void scheduledUpdate_repaintsDynamicItems() {
        engine.open(player, ScheduledView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);
        Inventory inventory = session.inventory();
        assertEquals(1, scheduledView.renders.get());
        assertEquals(1, inventory.getItem(0).getAmount());

        server.getScheduler().performTicks(2);

        assertEquals(2, scheduledView.renders.get());
        assertEquals(2, inventory.getItem(0).getAmount());
    }

    @Test
    void sharedStateSet_onMain_flushesEverySessionOfTheView() {
        engine.open(player, SharedView.class, ViewArguments.empty());
        engine.open(second, SharedView.class, ViewArguments.empty());
        assertEquals(2, sharedView.renders.get());

        sharedView.shared.set("changed");

        assertEquals(4, sharedView.renders.get(),
                "both sessions of the owning view must repaint exactly once");
    }

    @Test
    void sharedStateSet_offMain_isScheduledAndFlushesNextTick() throws InterruptedException {
        engine.open(player, SharedView.class, ViewArguments.empty());
        engine.open(second, SharedView.class, ViewArguments.empty());
        assertEquals(2, sharedView.renders.get());

        Thread writer = new Thread(() -> sharedView.shared.set("background"));
        writer.start();
        writer.join();

        assertEquals(2, sharedView.renders.get(),
                "off-main writes must not flush synchronously");

        server.getScheduler().performTicks(1);

        assertEquals(4, sharedView.renders.get());
    }

    @Test
    void sharedStateSets_offMain_coalesceToOneFlushPerSessionPerTick() throws InterruptedException {
        engine.open(player, SharedView.class, ViewArguments.empty());
        engine.open(second, SharedView.class, ViewArguments.empty());
        assertEquals(2, sharedView.renders.get());

        Thread writer = new Thread(() -> {
            sharedView.shared.set("a");
            sharedView.shared.set("b");
        });
        writer.start();
        writer.join();
        server.getScheduler().performTicks(1);

        assertEquals(4, sharedView.renders.get(),
                "two pre-tick writes must coalesce into one flush pass per session");
    }

    static final class ReactiveView extends View {
        final MutableState<Integer> counter = mutableState(0);
        final AtomicInteger watchedRenders = new AtomicInteger();
        final AtomicInteger unwatchedRenders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Reactive").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        watchedRenders.incrementAndGet();
                        Integer value = counter.get(ctx);
                        return new ItemStack(value != null && value > 0
                                ? Material.DIAMOND : Material.EMERALD);
                    })
                    .updateOnStateChange(counter);
            render.slot(1)
                    .item(ctx -> new ItemStack(Material.PAPER, unwatchedRenders.incrementAndGet()));
            render.slot(2, new ItemStack(Material.STONE))
                    .onClick(ctx -> counter.update(ctx, value -> value + 1));
        }
    }

    static final class CascadeView extends View {
        final MutableState<Integer> token = mutableState(0);
        final AtomicInteger updates = new AtomicInteger();
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Cascade").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        renders.incrementAndGet();
                        return new ItemStack(Material.PAPER);
                    })
                    .updateOnStateChange(token);
        }

        @Override
        protected void onUpdate(@NotNull UpdateContext context) {
            // dirty the token on the first pass only: exactly one cascade expected
            if (updates.incrementAndGet() == 1) {
                token.set(context, 1);
            }
        }
    }

    static final class FeedbackView extends View {
        final MutableState<Integer> token = mutableState(0);
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Feedback").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        renders.incrementAndGet();
                        // self-feeding: every render re-dirties the watched token
                        token.update(ctx, value -> value + 1);
                        return new ItemStack(Material.PAPER);
                    })
                    .updateOnStateChange(token);
        }
    }

    static final class ScheduledView extends View {
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Scheduled").rows(1).scheduleUpdate(2);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> new ItemStack(Material.PAPER, renders.incrementAndGet()));
        }
    }

    static final class SharedView extends View {
        final SharedState<String> shared = sharedState("initial");
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Shared").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        renders.incrementAndGet();
                        return new ItemStack(Material.PAPER);
                    })
                    .updateOnStateChange(shared);
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        boolean hasWarningContaining(String fragment) {
            for (LogRecord record : records) {
                if (record.getLevel() == Level.WARNING && record.getMessage() != null
                        && record.getMessage().contains(fragment)) {
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
