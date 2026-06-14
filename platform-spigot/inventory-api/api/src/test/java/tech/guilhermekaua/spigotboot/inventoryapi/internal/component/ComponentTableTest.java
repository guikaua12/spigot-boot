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
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTableTest {

    private ComponentTable table;

    @BeforeEach
    void setUp() {
        table = new ComponentTable();
    }

    @Test
    void add_thenComponentAt_returnsComponentForEverySlot() {
        ComponentInstance component = component(0, 5, 8);

        table.add(component);

        assertSame(component, table.componentAt(0));
        assertSame(component, table.componentAt(5));
        assertSame(component, table.componentAt(8));
        assertEquals(Collections.singletonList(component), table.all());
    }

    @Test
    void componentAt_unboundSlot_returnsNull() {
        table.add(component(0));

        assertNull(table.componentAt(1));
    }

    @Test
    void add_overlappingSlot_throwsAndLeavesTableUnchanged() {
        ComponentInstance first = component(0, 1);
        table.add(first);

        ViewConfigurationException exception = assertThrows(ViewConfigurationException.class,
                () -> table.add(component(1, 2)));

        assertTrue(exception.getMessage().contains("slot 1"));
        assertSame(first, table.componentAt(1));
        assertNull(table.componentAt(2));
        assertEquals(1, table.all().size());
    }

    @Test
    void add_missingItemSource_throwsViewConfigurationException() {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.onClick(click -> {
        });
        ComponentInstance component = builder.materialize(new int[]{3});

        assertThrows(ViewConfigurationException.class, () -> table.add(component));
    }

    @Test
    void watchersOf_intersectingDirtyIds_returnsOnlyWatchers() {
        ComponentInstance watcherOfThree = watching(3, 0);
        ComponentInstance watcherOfNine = watching(9, 1);
        ComponentInstance unwatched = component(2);
        table.add(watcherOfThree);
        table.add(watcherOfNine);
        table.add(unwatched);

        List<ComponentInstance> watchers = table.watchersOf(new HashSet<>(Arrays.asList(3, 99)));

        assertEquals(Collections.singletonList(watcherOfThree), watchers);
    }

    @Test
    void watchersOf_emptyDirtySet_returnsEmpty() {
        table.add(watching(3, 0));

        assertTrue(table.watchersOf(Collections.<Integer>emptySet()).isEmpty());
    }

    @Test
    void all_isUnmodifiable() {
        table.add(component(0));
        List<ComponentInstance> all = table.all();

        assertThrows(UnsupportedOperationException.class, all::clear);
    }

    private static ComponentInstance component(int... slots) {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(new ItemStack(Material.STONE));
        return builder.materialize(slots);
    }

    private static ComponentInstance watching(int tokenId, int... slots) {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(new ItemStack(Material.STONE));
        builder.updateOnStateChange(new StubToken(tokenId));
        return builder.materialize(slots);
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
}
