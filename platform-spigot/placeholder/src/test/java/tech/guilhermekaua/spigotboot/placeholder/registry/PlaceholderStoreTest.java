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
package tech.guilhermekaua.spigotboot.placeholder.registry;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.placeholder.metadata.PlaceholderMetadata;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceholderStoreTest {

    @Test
    void registerThenFindByExactKeyReturnsMetadata() {
        PlaceholderStore store = new PlaceholderStore();
        PlaceholderMetadata metadata = mock(PlaceholderMetadata.class);
        when(metadata.getPlaceholder()).thenReturn("user_name");

        store.register(metadata);

        assertSame(metadata, store.findPlaceholderMetadata("user_name"));
    }

    @Test
    void findResolvesByPatternWhenNoExactMatch() {
        PlaceholderStore store = new PlaceholderStore();
        PlaceholderMetadata metadata = mock(PlaceholderMetadata.class);
        when(metadata.getPlaceholder()).thenReturn("top_<page>");
        store.register(metadata);

        // "top_3" is not an exact key, but matches the registered pattern
        assertSame(metadata, store.findPlaceholderMetadata("top_3"));
        // a value that does not satisfy the pattern must not match
        assertNull(store.findPlaceholderMetadata("bottom_3"));
    }

    @Test
    void findReturnsNullWhenNoMatch() {
        PlaceholderStore store = new PlaceholderStore();

        assertNull(store.findPlaceholderMetadata("missing"));
    }

    @Test
    void reRegisteringSameKeyOverwritesMetadata() {
        PlaceholderStore store = new PlaceholderStore();
        PlaceholderMetadata first = mock(PlaceholderMetadata.class);
        when(first.getPlaceholder()).thenReturn("user_name");
        PlaceholderMetadata second = mock(PlaceholderMetadata.class);
        when(second.getPlaceholder()).thenReturn("user_name");

        store.register(first);
        store.register(second);

        assertSame(second, store.findPlaceholderMetadata("user_name"));
    }

    @Test
    void clearRemovesRegisteredPlaceholders() {
        PlaceholderStore store = new PlaceholderStore();
        PlaceholderMetadata metadata = mock(PlaceholderMetadata.class);
        when(metadata.getPlaceholder()).thenReturn("user_name");
        store.register(metadata);

        store.clear();

        assertNull(store.findPlaceholderMetadata("user_name"));
    }
}
