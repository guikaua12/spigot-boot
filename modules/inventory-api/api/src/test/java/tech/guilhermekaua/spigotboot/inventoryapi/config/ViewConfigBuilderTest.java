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
package tech.guilhermekaua.spigotboot.inventoryapi.config;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewConfigBuilderTest {

    @Test
    void build_appliesDefaults() {
        ViewConfig config = new ViewConfigBuilder().title("&aShop").rows(3).build();

        assertEquals("&aShop", config.title());
        assertEquals(3, config.rows());
        assertEquals(Collections.emptyList(), config.layout());
        assertTrue(config.cancelOnClick());
        assertTrue(config.cancelOnDrag());
        assertEquals(0L, config.updateIntervalTicks());
        assertTrue(config.applyPlaceholders());
    }

    @Test
    void setters_areFluentAndStored() {
        ViewConfig config = new ViewConfigBuilder()
                .title("Shop")
                .rows(2)
                .cancelOnClick(false)
                .cancelOnDrag(false)
                .scheduleUpdate(20L)
                .applyPlaceholders(false)
                .build();

        assertFalse(config.cancelOnClick());
        assertFalse(config.cancelOnDrag());
        assertEquals(20L, config.updateIntervalTicks());
        assertFalse(config.applyPlaceholders());
    }

    @Test
    void scheduleUpdate_nonPositiveDisables() {
        ViewConfig config = new ViewConfigBuilder().title("Shop").rows(1).scheduleUpdate(-5L).build();

        assertEquals(0L, config.updateIntervalTicks());
    }

    @Test
    void build_missingTitle_throws() {
        ViewConfigBuilder builder = new ViewConfigBuilder().rows(3);

        assertThrows(ViewConfigurationException.class, builder::build);
    }

    @Test
    void build_rowsOutsideChestRange_throws() {
        assertThrows(ViewConfigurationException.class,
                () -> new ViewConfigBuilder().title("Shop").rows(0).build());
        assertThrows(ViewConfigurationException.class,
                () -> new ViewConfigBuilder().title("Shop").rows(7).build());
    }

    @Test
    void build_layoutRowNotNineChars_throws() {
        ViewConfigBuilder builder = new ViewConfigBuilder().title("Shop").layout("        "); // 8 chars

        assertThrows(ViewConfigurationException.class, builder::build);
    }

    @Test
    void build_rowsAndLayoutInconsistent_throws() {
        ViewConfigBuilder builder = new ViewConfigBuilder()
                .title("Shop")
                .rows(3)
                .layout("         ");

        assertThrows(ViewConfigurationException.class, builder::build);
    }

    @Test
    void build_rowsAndLayoutConsistent_passes() {
        ViewConfig config = new ViewConfigBuilder()
                .title("Shop")
                .rows(2)
                .layout("         ", "   AAA   ")
                .build();

        assertEquals(2, config.rows());
        assertEquals(Arrays.asList("         ", "   AAA   "), config.layout());
    }

    @Test
    void build_neitherRowsNorLayout_throws() {
        ViewConfigBuilder builder = new ViewConfigBuilder().title("Shop");

        assertThrows(ViewConfigurationException.class, builder::build);
    }

    @Test
    void build_infersRowsFromLayout() {
        ViewConfig config = new ViewConfigBuilder()
                .title("Shop")
                .layout("         ", "         ", "         ")
                .build();

        assertEquals(3, config.rows());
    }

    @Test
    void layout_isUnmodifiable() {
        ViewConfig config = new ViewConfigBuilder().title("Shop").layout("         ").build();

        assertThrows(UnsupportedOperationException.class, () -> config.layout().add("         "));
    }

    @Test
    void withOverrides_appliesNonNullValuesOnNewInstance() {
        ViewConfig original = new ViewConfigBuilder().title("Shop").rows(3).cancelOnClick(false).build();

        ViewConfig overridden = original.withOverrides("Bank", 6);

        assertNotSame(original, overridden);
        assertEquals("Bank", overridden.title());
        assertEquals(6, overridden.rows());
        // non-overridable settings carry over unchanged
        assertFalse(overridden.cancelOnClick());
        // original is untouched
        assertEquals("Shop", original.title());
        assertEquals(3, original.rows());
    }

    @Test
    void withOverrides_nullValuesKeepOriginal() {
        ViewConfig original = new ViewConfigBuilder().title("Shop").rows(3).build();

        ViewConfig overridden = original.withOverrides(null, null);

        assertEquals("Shop", overridden.title());
        assertEquals(3, overridden.rows());
    }
}
