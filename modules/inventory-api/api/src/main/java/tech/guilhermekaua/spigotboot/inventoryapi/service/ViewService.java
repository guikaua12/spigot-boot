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

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;

import java.util.Objects;
import java.util.Optional;

/**
 * DI facade for opening and closing views. All methods must be called on the main thread.
 */
@Service
public final class ViewService {

    private final ViewEngine engine;
    private final SessionRegistry sessions;

    /**
     * Creates the service.
     *
     * @param engine   the view engine executing lifecycle operations
     * @param sessions the per-player session registry
     */
    public ViewService(ViewEngine engine, SessionRegistry sessions) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null.");
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null.");
    }

    /**
     * Opens a registered view for the player without arguments.
     *
     * @param player the viewer
     * @param view   the registered view class
     * @throws UnknownViewException  if the view class is not registered
     * @throws IllegalStateException if called off the main thread
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> view) {
        open(player, view, ViewArguments.empty());
    }

    /**
     * Opens a registered view for the player with the given arguments.
     *
     * @param player    the viewer
     * @param view      the registered view class
     * @param arguments the open arguments
     * @throws UnknownViewException  if the view class is not registered
     * @throws IllegalStateException if called off the main thread
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> view,
                     @NotNull ViewArguments arguments) {
        ViewEngine.assertMainThread("ViewService.open");
        engine.open(player, view, arguments);
    }

    /**
     * Closes the player's open view; no-op when none is open.
     *
     * @param player the viewer whose view should close
     * @throws IllegalStateException if called off the main thread
     */
    public void close(@NotNull Player player) {
        ViewEngine.assertMainThread("ViewService.close");
        sessions.find(player.getUniqueId())
                .ifPresent(session -> engine.close(session, CloseReason.API));
    }

    /**
     * Returns a live context for the player's open view, if any.
     *
     * @param player the viewer to look up
     * @return the live context, or empty when the player has no open view
     */
    public @NotNull Optional<ViewContext> contextOf(@NotNull Player player) {
        return sessions.find(player.getUniqueId())
                .map(session -> (ViewContext) new PlainViewContextImpl(session, engine));
    }
}
