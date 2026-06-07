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
package tech.guilhermekaua.spigotboot.inventoryapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewArgumentsTest {

    @Test
    void empty_hasNoKeys() {
        ViewArguments arguments = ViewArguments.empty();

        assertFalse(arguments.has("anything"));
        assertNull(arguments.get("anything", String.class));
    }

    @Test
    void of_singlePair_storesValue() {
        ViewArguments arguments = ViewArguments.of("name", "Steve");

        assertTrue(arguments.has("name"));
        assertEquals("Steve", arguments.get("name", String.class));
    }

    @Test
    void of_twoPairs_storesBothValues() {
        ViewArguments arguments = ViewArguments.of("name", "Steve", "count", 3);

        assertEquals("Steve", arguments.get("name", String.class));
        assertEquals(3, arguments.get("count", Integer.class));
    }

    @Test
    void builder_storesAllPairs() {
        ViewArguments arguments = ViewArguments.builder()
                .put("a", 1)
                .put("b", 2L)
                .put("c", "three")
                .build();

        assertEquals(1, arguments.get("a", Integer.class));
        assertEquals(2L, arguments.get("b", Long.class));
        assertEquals("three", arguments.get("c", String.class));
    }

    @Test
    void get_absentKey_returnsNull() {
        ViewArguments arguments = ViewArguments.of("name", "Steve");

        assertNull(arguments.get("missing", String.class));
    }

    @Test
    void get_wrongType_throwsNamingKeyAndTypes() {
        ViewArguments arguments = ViewArguments.of("count", 3);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> arguments.get("count", String.class));

        assertTrue(error.getMessage().contains("count"));
        assertTrue(error.getMessage().contains(String.class.getName()));
        assertTrue(error.getMessage().contains(Integer.class.getName()));
    }

    @Test
    void require_presentKey_returnsValue() {
        ViewArguments arguments = ViewArguments.of("count", 3);

        assertEquals(3, arguments.require("count", Integer.class));
    }

    @Test
    void require_absentKey_throwsNamingKey() {
        ViewArguments arguments = ViewArguments.empty();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> arguments.require("missing", Integer.class));

        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void require_wrongType_throws() {
        ViewArguments arguments = ViewArguments.of("count", 3);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> arguments.require("count", String.class));

        assertTrue(error.getMessage().contains("count"));
        assertTrue(error.getMessage().contains(String.class.getName()));
        assertTrue(error.getMessage().contains(Integer.class.getName()));
    }

    @Test
    void has_reportsPresence() {
        ViewArguments arguments = ViewArguments.of("name", "Steve");

        assertTrue(arguments.has("name"));
        assertFalse(arguments.has("other"));
    }
}
