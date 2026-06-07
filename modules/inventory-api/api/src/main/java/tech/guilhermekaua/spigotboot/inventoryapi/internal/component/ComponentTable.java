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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-session component registry: maps slots to their owning component and resolves the
 * components watching a set of dirty token ids. Rejects slot overlaps and components
 * without an item source at registration time.
 */
@ApiStatus.Internal
public final class ComponentTable {

    private final Map<Integer, ComponentInstance> bySlot = new HashMap<>();
    private final List<ComponentInstance> components = new ArrayList<>();

    /**
     * Registers a component; validated before any slot is bound so a failed add leaves
     * the table unchanged.
     *
     * @throws ViewConfigurationException on slot overlap or when the component declares no item source
     */
    public void add(@NotNull ComponentInstance component) {
        if (!component.hasItemSource()) {
            throw new ViewConfigurationException("component for slots "
                    + Arrays.toString(component.slots())
                    + " declares no item source; call item(ItemStack) or item(Function)");
        }

        int[] slots = component.slots();
        for (int slot : slots) {
            if (bySlot.containsKey(slot)) {
                throw new ViewConfigurationException(
                        "slot " + slot + " is already bound to another component");
            }
        }
        for (int slot : slots) {
            bySlot.put(slot, component);
        }
        components.add(component);
    }

    /**
     * @return the component bound to the slot, or null when the slot is component-less
     */
    public @Nullable ComponentInstance componentAt(int slot) {
        return bySlot.get(slot);
    }

    /**
     * @return all registered components in declaration order; unmodifiable
     */
    public @NotNull List<ComponentInstance> all() {
        return Collections.unmodifiableList(components);
    }

    /**
     * Resolves the components whose watched token ids intersect the dirty set,
     * in declaration order.
     */
    public @NotNull List<ComponentInstance> watchersOf(@NotNull Set<Integer> dirtyTokenIds) {
        List<ComponentInstance> watchers = new ArrayList<>();
        for (ComponentInstance component : components) {
            for (int tokenId : component.watchedTokenIdsInternal()) {
                if (dirtyTokenIds.contains(tokenId)) {
                    watchers.add(component);
                    break;
                }
            }
        }
        return watchers;
    }
}
