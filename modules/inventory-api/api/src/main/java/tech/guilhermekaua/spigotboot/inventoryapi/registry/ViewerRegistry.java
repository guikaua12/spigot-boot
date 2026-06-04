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
package tech.guilhermekaua.spigotboot.inventoryapi.registry;

import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the live viewers of every open custom inventory, keyed by player UUID. Replaces the
 * upstream {@code ViewerController} static singleton with a DI-managed bean.
 */
@Component
public final class ViewerRegistry {

    private final Map<UUID, Viewer> viewerMap = new ConcurrentHashMap<>();

    public Viewer registerViewer(Viewer viewer) {
        this.viewerMap.put(viewer.getUniqueId(), viewer);
        return viewer;
    }

    public Viewer unregisterViewer(Player player) {
        return this.viewerMap.remove(player.getUniqueId());
    }

    public Viewer unregisterViewer(Viewer viewer) {
        return this.viewerMap.remove(viewer.getUniqueId());
    }

    public Optional<Viewer> findViewer(Player player) {
        return Optional.ofNullable(this.viewerMap.get(player.getUniqueId()));
    }

    public Collection<Viewer> findAll() {
        return Collections.unmodifiableCollection(this.viewerMap.values());
    }

}
