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
package tech.guilhermekaua.spigotboot.inventoryapi.viewer;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

/**
 * Holds the framework-wide collaborators that every {@link Viewer} needs at runtime — the
 * registry it belongs to, the title updater it should call when retitling, and the Bukkit
 * plugin used to schedule tasks. Constructed once at boot by {@code InventoryService} and
 * shared across every {@link Viewer} the service produces.
 *
 * <p>Replaces the static lookups against the upstream {@code InventoryManager} singleton
 * with constructor-passed collaborators, removing the framework's last reach into static
 * state.
 */
@Value
@RequiredArgsConstructor
public class ViewerContext {
    ViewerRegistry registry;
    TitleUpdater titleUpdater;
    Plugin plugin;
}
