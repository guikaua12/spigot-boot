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
package tech.guilhermekaua.spigotboot.inventoryapi.item.callback;

import lombok.Data;
import org.bukkit.event.inventory.ClickType;
import tech.guilhermekaua.spigotboot.inventoryapi.event.impl.CustomInventoryClickEvent;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.update.ItemUpdateCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Holds the click handlers and update callback associated with a single item slot. Click handlers
 * are keyed by {@link ClickType}; a {@code null} key registers the default fallback that fires
 * when no specific click-type handler is configured.
 */
@Data
public final class ItemCallback {

    private final Map<ClickType, Consumer<CustomInventoryClickEvent>> callbackMap = new HashMap<>();
    private ItemUpdateCallback updateCallback;

    public void callback(ClickType clickType, Consumer<CustomInventoryClickEvent> eventConsumer) {
        this.callbackMap.put(clickType, eventConsumer);
    }

    public Consumer<CustomInventoryClickEvent> getClickCallback(ClickType clickType) {
        return this.callbackMap.getOrDefault(clickType, this.callbackMap.get(null));
    }

}
