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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Single Bukkit listener bridge of the v3 view engine: resolves the clicking, dragging,
 * closing, quitting or disabling player's session in the {@link SessionRegistry} and
 * delegates to the matching {@link ViewEngine} entry point. Events of players without a
 * session are ignored.
 *
 * <p>Auto-registered with Bukkit by spigot-boot's {@code BukkitListenerAutoRegistrar} when
 * the context becomes ready — no manual {@code registerEvents} call required.
 */
@Component
@ApiStatus.Internal
public final class ViewListener implements Listener {

    private final SessionRegistry sessions;
    private final ViewEngine engine;

    /**
     * Creates the listener.
     *
     * @param sessions the per-player session registry
     * @param engine   the engine receiving the bridged events
     */
    public ViewListener(@NotNull SessionRegistry sessions, @NotNull ViewEngine engine) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Routes inventory clicks of players with an open view session into the engine.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        sessions.find(event.getWhoClicked().getUniqueId())
                .ifPresent(session -> engine.click(session, event));
    }

    /**
     * Applies the session drag policy to drags of players with an open view session.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        sessions.find(event.getWhoClicked().getUniqueId())
                .ifPresent(session -> engine.drag(session, event));
    }

    /**
     * Bridges container closes into the engine; the engine guards by container identity.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        sessions.find(event.getPlayer().getUniqueId())
                .ifPresent(session -> engine.bukkitClose(session, event));
    }

    /**
     * Closes the quitting player's session with the DISCONNECT reason.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.find(event.getPlayer().getUniqueId())
                .ifPresent(session -> engine.close(session, CloseReason.DISCONNECT));
    }

    /**
     * Closes every open session when the plugin owning the inventory-api runtime disables.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != engine.plugin()) {
            return;
        }
        // closing unregisters sessions while iterating; copy the collection first
        for (ViewSession session : new ArrayList<>(sessions.all())) {
            engine.close(session, CloseReason.PLUGIN_DISABLE);
        }
    }
}
