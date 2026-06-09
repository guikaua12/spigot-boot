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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.registry;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.ViewDiscoveryService;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewRegistryTest {

    public static final class CountingView extends View {
        int onInitCalls;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            onInitCalls++;
            config.title("Counting").rows(1);
        }

        MutableState<String> declareLateState() {
            return mutableState("late");
        }
    }

    public static final class SecondView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Second").rows(2);
        }
    }

    public static final class BadConfigView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            // neither rows nor layout: build() must reject this config
            config.title("Bad");
        }
    }

    @Test
    void register_runsOnInitExactlyOnce() {
        ViewRegistry registry = new ViewRegistry();
        CountingView view = new CountingView();

        registry.register(view);

        assertEquals(1, view.onInitCalls);
    }

    @Test
    void register_freezesTokenTable_stateFactoriesThrowAfterwards() {
        ViewRegistry registry = new ViewRegistry();
        CountingView view = new CountingView();

        registry.register(view);

        assertTrue(view.tokenTable().isFrozen());
        assertThrows(IllegalStateException.class, view::declareLateState);
    }

    @Test
    void register_badConfig_propagatesViewConfigurationException() {
        ViewRegistry registry = new ViewRegistry();

        assertThrows(ViewConfigurationException.class, () -> registry.register(new BadConfigView()));
        assertFalse(registry.find(BadConfigView.class).isPresent());
    }

    @Test
    void findAndAll_exposeRegistrations() {
        ViewRegistry registry = new ViewRegistry();
        CountingView counting = new CountingView();
        SecondView second = new SecondView();
        registry.register(counting);
        registry.register(second);

        Optional<RegisteredView> found = registry.find(CountingView.class);
        assertTrue(found.isPresent());
        assertEquals(CountingView.class, found.get().type());
        assertSame(counting, found.get().instance());
        assertEquals("Counting", found.get().config().title());

        assertFalse(registry.find(BadConfigView.class).isPresent());
        assertEquals(2, registry.all().size());
    }

    @Test
    void register_duplicateType_throwsIllegalStateException() {
        ViewRegistry registry = new ViewRegistry();
        CountingView first = new CountingView();
        registry.register(first);

        CountingView second = new CountingView();
        assertThrows(IllegalStateException.class, () -> registry.register(second));

        // the duplicate is rejected before its onInit runs and the first registration survives
        assertEquals(0, second.onInitCalls);
        assertSame(first, registry.find(CountingView.class).map(RegisteredView::instance).orElse(null));
    }

    @Test
    void diConstructor_supportsDirectRegistration() {
        ViewRegistry registry = new ViewRegistry(new ViewDiscoveryService());
        CountingView view = new CountingView();

        registry.register(view);

        assertTrue(registry.find(CountingView.class).isPresent());
    }
}
