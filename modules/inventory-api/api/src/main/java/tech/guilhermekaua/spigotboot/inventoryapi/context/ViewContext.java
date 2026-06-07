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
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.UUID;

/**
 * Base contract shared by every per-phase context handed to {@link View} lifecycle handlers.
 *
 * <p>A context is bound to one (player, open) session. The mutating methods
 * ({@link #update()}, {@link #close()}, {@link #updateTitle(String)},
 * {@link #openView(Class)}) are main-thread only and throw {@link IllegalStateException}
 * when invoked off-main.
 */
@ApiStatus.NonExtendable
public interface ViewContext {

    /**
     * Returns the player viewing this session.
     *
     * @return the viewer
     */
    @NotNull Player player();

    /**
     * Returns the unique id of {@link #player()}.
     *
     * @return the viewer's UUID
     */
    @NotNull UUID playerId();

    /**
     * Returns the view singleton this context belongs to.
     *
     * @return the owning view instance
     */
    @NotNull View view();

    /**
     * Returns the effective configuration of this session, with per-open overrides
     * recorded in {@code onOpen} already applied.
     *
     * @return the effective, immutable view config
     */
    @NotNull ViewConfig config();

    /**
     * Returns the plugin that owns the inventory-api runtime.
     *
     * @return the owning plugin
     */
    @NotNull Plugin plugin();

    /**
     * Returns the arguments this session was opened with; empty when none were passed.
     *
     * @return the open arguments
     */
    @NotNull ViewArguments arguments();

    /**
     * Returns the top container of this session.
     *
     * @return the Bukkit inventory backing this view
     * @throws IllegalStateException before the container is created (e.g. inside
     *         {@code onOpen}) and after the session closed
     */
    @NotNull Inventory inventory();

    /**
     * Returns whether this session is currently active (open, not transitioning or closed).
     *
     * @return {@code true} while the session is active
     */
    boolean isActive();

    /**
     * Schedules a full update pass ({@link UpdateTrigger#EXPLICIT}); multiple calls in the
     * same tick are coalesced. Main thread only.
     */
    void update();

    /**
     * Closes this session. When called during click dispatch the close is deferred to the
     * end of the current tick and further clicks are swallowed. Outside click dispatch,
     * the session closes synchronously before this method returns. Main thread only.
     */
    void close();

    /**
     * Updates the container title in place (no reopen) via the NMS title updater;
     * placeholders are applied for {@link #player()}. Main thread only.
     *
     * @param title the new title, legacy color codes supported
     */
    void updateTitle(@NotNull String title);

    /**
     * Navigates to another registered view: this session closes with
     * {@link CloseReason#REPLACED}, then the target opens. Deferred to the end of the tick
     * during click dispatch. Calling this method from within {@code onClose} is illegal;
     * the call is logged SEVERE and dropped to prevent navigation loops. Main thread only.
     *
     * @param target the registered view class to open
     * @throws UnknownViewException when the target class is not registered
     */
    void openView(@NotNull Class<? extends View> target);

    /**
     * Same as {@link #openView(Class)}, passing arguments to the target view.
     * Calling this method from within {@code onClose} is illegal; the call is logged
     * SEVERE and dropped to prevent navigation loops.
     *
     * @param target    the registered view class to open
     * @param arguments the arguments handed to the target's contexts
     * @throws UnknownViewException when the target class is not registered
     */
    void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments);
}
