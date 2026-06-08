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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.render;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SlotPainterTest {

    private ServerMock server;
    private PlayerMock player;
    private Inventory inventory;
    private PlaceholderApplier placeholderApplier;
    private SlotPainter painter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("tester");
        inventory = Bukkit.createInventory(null, 27);
        placeholderApplier = mock(PlaceholderApplier.class);
        painter = new SlotPainter(placeholderApplier);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void paint_nullItem_clearsSlot() {
        inventory.setItem(4, new ItemStack(Material.STONE));

        painter.paint(player, inventory, 4, null, true);

        assertNull(inventory.getItem(4));
        verifyNoInteractions(placeholderApplier);
    }

    @Test
    void paint_applyPlaceholders_appliesNameAndLoreToCloneOnly() {
        ItemStack original = new ItemStack(Material.DIAMOND);
        ItemMeta meta = original.getItemMeta();
        meta.setDisplayName("name %placeholder%");
        meta.setLore(Collections.singletonList("lore %placeholder%"));
        original.setItemMeta(meta);

        when(placeholderApplier.apply(player, "name %placeholder%")).thenReturn("applied name");
        when(placeholderApplier.applyAll(player, Collections.singletonList("lore %placeholder%")))
                .thenReturn(Collections.singletonList("applied lore"));

        painter.paint(player, inventory, 0, original, true);

        ItemStack painted = inventory.getItem(0);
        assertNotNull(painted);
        assertEquals(Material.DIAMOND, painted.getType());
        assertEquals("applied name", painted.getItemMeta().getDisplayName());
        assertEquals(Collections.singletonList("applied lore"), painted.getItemMeta().getLore());

        // the caller's item must stay untouched
        assertEquals("name %placeholder%", original.getItemMeta().getDisplayName());
        assertEquals(Collections.singletonList("lore %placeholder%"), original.getItemMeta().getLore());
    }

    @Test
    void paint_applyPlaceholders_itemWithoutMeta_paintsWithoutApplier() {
        ItemStack original = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(original.clone()).thenReturn(clone);
        when(clone.clone()).thenReturn(clone);
        when(clone.getItemMeta()).thenReturn(null);

        painter.paint(player, inventory, 3, original, true);

        verify(original).clone();
        verify(clone, never()).setItemMeta(any());
        verifyNoInteractions(placeholderApplier);
    }

    @Test
    void paint_withoutPlaceholders_setsValueEqualItemAndSkipsApplier() {
        ItemStack original = new ItemStack(Material.GOLD_INGOT, 5);

        painter.paint(player, inventory, 2, original, false);

        ItemStack painted = inventory.getItem(2);
        assertNotNull(painted);
        assertEquals(original, painted);
        assertNotSame(original, painted);
        verifyNoInteractions(placeholderApplier);
    }

    @Test
    void paint_withoutPlaceholders_clonesTheCallerItem() {
        ItemStack original = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(original.clone()).thenReturn(clone);
        when(clone.clone()).thenReturn(clone);

        painter.paint(player, inventory, 5, original, false);

        verify(original).clone();
        verifyNoInteractions(placeholderApplier);
    }
}
