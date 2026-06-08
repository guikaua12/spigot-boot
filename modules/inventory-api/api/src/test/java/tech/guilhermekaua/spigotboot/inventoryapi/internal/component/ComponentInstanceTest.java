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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.component;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ComponentInstanceTest {

    private static final Logger COMPONENT_LOGGER = Logger.getLogger(ComponentInstance.class.getName());

    private final ViewContext context = mock(ViewContext.class);
    private CapturingHandler logHandler;

    @BeforeEach
    void setUp() {
        logHandler = new CapturingHandler();
        COMPONENT_LOGGER.addHandler(logHandler);
    }

    @AfterEach
    void tearDown() {
        COMPONENT_LOGGER.removeHandler(logHandler);
    }

    @Test
    void slots_returnsMaterializedSlots() {
        ComponentInstance component = staticItemBuilder().materialize(new int[]{1, 4, 7});

        assertArrayEquals(new int[]{1, 4, 7}, component.slots());
    }

    @Test
    void isVisible_withoutDisplayIf_returnsTrue() {
        ComponentInstance component = staticItemBuilder().materialize(new int[]{0});

        assertTrue(component.isVisible(context));
    }

    @Test
    void isVisible_predicateReceivesContextAndDecides() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        List<ViewContext> seen = new ArrayList<>();
        builder.displayIf(ctx -> {
            seen.add(ctx);
            return false;
        });
        ComponentInstance component = builder.materialize(new int[]{0});

        assertFalse(component.isVisible(context));
        assertEquals(Collections.singletonList(context), seen);
    }

    @Test
    void isVisible_predicateThrows_returnsFalseAndLogsAtLeastOnce() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        builder.displayIf(ctx -> {
            throw new IllegalStateException("boom");
        });
        ComponentInstance component = builder.materialize(new int[]{0});

        assertFalse(component.isVisible(context));
        assertFalse(component.isVisible(context));
        assertTrue(logHandler.records.size() >= 1, "expected at least one rate-limited log");
    }

    @Test
    void renderItem_staticItem_returnsConfiguredItem() {
        ItemStack item = new ItemStack(Material.STONE);
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(item);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertSame(item, component.renderItem(context));
    }

    @Test
    void renderItem_renderer_appliesWithContext() {
        ItemStack item = new ItemStack(Material.STONE);
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(ctx -> ctx == context ? item : null);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertSame(item, component.renderItem(context));
    }

    @Test
    void renderItem_rendererThrows_returnsNullAndLogsAtLeastOnce() {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(ctx -> {
            throw new IllegalStateException("boom");
        });
        ComponentInstance component = builder.materialize(new int[]{0});

        assertNull(component.renderItem(context));
        assertTrue(logHandler.records.size() >= 1, "expected at least one rate-limited log");
    }

    @Test
    void handlerFor_typedMatch_returnsTypedHandler() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        Consumer<SlotClickContext> typed = click -> {
        };
        Consumer<SlotClickContext> untyped = click -> {
        };
        builder.onClick(ClickType.LEFT, typed);
        builder.onClick(untyped);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertSame(typed, component.handlerFor(ClickType.LEFT));
    }

    @Test
    void handlerFor_typedMiss_fallsBackToUntypedHandler() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        Consumer<SlotClickContext> typed = click -> {
        };
        Consumer<SlotClickContext> untyped = click -> {
        };
        builder.onClick(ClickType.LEFT, typed);
        builder.onClick(untyped);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertSame(untyped, component.handlerFor(ClickType.RIGHT));
    }

    @Test
    void handlerFor_noHandlers_returnsNull() {
        ComponentInstance component = staticItemBuilder().materialize(new int[]{0});

        assertNull(component.handlerFor(ClickType.LEFT));
    }

    @Test
    void defaults_inheritCancelAndDeclareNoPostActions() {
        ComponentInstance component = staticItemBuilder().materialize(new int[]{0});

        assertNull(component.cancelOnClick());
        assertFalse(component.closeOnClick());
        assertNull(component.openOnClickTarget());
        assertFalse(component.openOnClickArguments().has("anything"));
        assertArrayEquals(new int[0], component.watchedTokenIds());
    }

    @Test
    void builderOptions_carryIntoInstance() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        ViewArguments arguments = ViewArguments.of("key", "value");
        builder.cancelOnClick(false);
        builder.closeOnClick();
        builder.openOnClick(TargetView.class, arguments);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertEquals(Boolean.FALSE, component.cancelOnClick());
        assertTrue(component.closeOnClick());
        assertEquals(TargetView.class, component.openOnClickTarget());
        assertSame(arguments, component.openOnClickArguments());
    }

    @Test
    void updateOnStateChange_identifiableTokens_resolveIdsInOrder() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        builder.updateOnStateChange(new StubToken(3), new StubToken(7));
        ComponentInstance component = builder.materialize(new int[]{0});

        assertArrayEquals(new int[]{3, 7}, component.watchedTokenIds());
    }

    @Test
    void updateOnStateChange_foreignToken_throwsIllegalArgumentException() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        StateToken foreign = new StateToken() {
        };

        assertThrows(IllegalArgumentException.class, () -> builder.updateOnStateChange(foreign));
    }

    @Test
    void renderForPaint_visibleComponent_returnsItem() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ComponentInstance component = builderWith(b -> b.item(item)).materialize(new int[]{0});

        assertSame(item, component.renderForPaint(context));
    }

    @Test
    void renderForPaint_hiddenComponent_returnsNullToClear() {
        ComponentInstance component = builderWith(b -> b
                .item(new ItemStack(Material.DIAMOND))
                .displayIf(ctx -> false)).materialize(new int[]{0});

        assertNull(component.renderForPaint(context));
    }

    @Test
    void renderForPaint_displayIfThrows_returnsFailureSentinel() {
        ComponentInstance component = builderWith(b -> b
                .item(new ItemStack(Material.DIAMOND))
                .displayIf(ctx -> { throw new IllegalStateException("boom"); })).materialize(new int[]{0});

        assertSame(ComponentInstance.RENDER_FAILURE, component.renderForPaint(context));
    }

    @Test
    void renderForPaint_rendererThrows_returnsFailureSentinel() {
        ComponentInstance component = builderWith(b -> b
                .item(ctx -> { throw new IllegalStateException("boom"); })).materialize(new int[]{0});

        assertSame(ComponentInstance.RENDER_FAILURE, component.renderForPaint(context));
    }

    @Test
    void renderForPaint_rendererReturnsNull_returnsNullToClearSlot() {
        ComponentInstance component = builderWith(b -> b.item(ctx -> null)).materialize(new int[]{0});

        assertNull(component.renderForPaint(context));
    }

    private static ItemComponentBuilderImpl staticItemBuilder() {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(new ItemStack(Material.STONE));
        return builder;
    }

    private static ItemComponentBuilderImpl builderWith(java.util.function.Consumer<ItemComponentBuilderImpl> configurator) {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        configurator.accept(builder);
        return builder;
    }

    private static final class TargetView extends View {
    }

    private static final class StubToken implements StateToken, IdentifiableToken {
        private final int id;

        private StubToken(int id) {
            this.id = id;
        }

        @Override
        public int tokenId() {
            return id;
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

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
