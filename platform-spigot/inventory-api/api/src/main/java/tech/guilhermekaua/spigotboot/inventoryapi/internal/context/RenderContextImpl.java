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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ItemComponentBuilderImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Context for {@code View.onFirstRender}: collects component declarations in order and
 * freezes them into the session's component table via {@link #materializeAll()}, called by
 * the first-render phase after the handler returns.
 */
@ApiStatus.Internal
public final class RenderContextImpl extends AbstractViewContext implements RenderContext {

    private final List<PendingComponent> pending = new ArrayList<>();
    private final Set<Character> boundLayoutChars = new LinkedHashSet<>();

    /**
     * Creates the context for one first render.
     *
     * @param session the session being rendered
     * @param engine  the engine executing the render
     */
    public RenderContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine) {
        super(session, engine);
    }

    @Override
    public @NotNull ItemComponentBuilder slot(int slot) {
        int size = session().effectiveConfig().rows() * Layout.ROW_WIDTH;
        if (slot < 0 || slot >= size) {
            throw new ViewConfigurationException("slot " + slot + " is out of bounds for a "
                    + session().effectiveConfig().rows() + "-row view (0-" + (size - 1) + ")");
        }
        return register(new int[]{slot});
    }

    @Override
    public @NotNull ItemComponentBuilder slot(int row, int column) {
        return slot((row - 1) * Layout.ROW_WIDTH + (column - 1));
    }

    @Override
    public @NotNull ItemComponentBuilder slot(int slot, @NotNull ItemStack item) {
        return slot(slot).item(item);
    }

    @Override
    public @NotNull ItemComponentBuilder layoutSlot(char character) {
        ResolvedLayout layout = session().layout();
        if (layout == null) {
            throw new ViewConfigurationException("no layout is defined for " + view().getClass().getName()
                    + "; layoutSlot('" + character + "') requires a config layout");
        }
        if (!layout.hasChar(character)) {
            throw new ViewConfigurationException("layout character '" + character
                    + "' is not present in the layout of " + view().getClass().getName());
        }
        // successful bindings feed the first-render unbound-layout-char warning
        boundLayoutChars.add(character);
        return register(layout.slotsOf(character));
    }

    @Override
    public @NotNull ItemComponentBuilder layoutSlot(char character, @NotNull ItemStack item) {
        return layoutSlot(character).item(item);
    }

    /**
     * Materializes every collected declaration into the session's component table, in
     * declaration order; the table validates slot overlaps and missing item sources.
     * idempotent: a second call is a no-op.
     *
     * @throws ViewConfigurationException when a declaration overlaps slots or has no item source
     */
    public void materializeAll() {
        for (PendingComponent declaration : pending) {
            session().components().add(declaration.builder.materialize(declaration.slots));
        }
        pending.clear();
    }

    /**
     * Returns the layout characters successfully bound through {@link #layoutSlot(char)}
     * during this render, in declaration order; the first-render phase subtracts them when
     * warning about layout characters bound to neither a component nor pagination.
     *
     * @return an unmodifiable view of the bound layout characters
     */
    public @NotNull Set<Character> boundLayoutChars() {
        return Collections.unmodifiableSet(boundLayoutChars);
    }

    private ItemComponentBuilder register(int[] slots) {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        pending.add(new PendingComponent(builder, slots));
        return builder;
    }

    /** one collected declaration: the mutable builder plus its resolved slots. */
    private static final class PendingComponent {

        private final ItemComponentBuilderImpl builder;
        private final int[] slots;

        private PendingComponent(ItemComponentBuilderImpl builder, int[] slots) {
            this.builder = builder;
            this.slots = slots;
        }
    }
}
