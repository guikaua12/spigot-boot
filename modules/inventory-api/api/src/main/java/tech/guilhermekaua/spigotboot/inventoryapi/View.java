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
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.InitialStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.LazyStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.MutableStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.SharedStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.State;

import java.util.function.Function;

/**
 * Base class for inventory views. Views are DI singletons discovered via
 * {@code @RegisterView} and opened through {@code ViewService}; subclasses override only the
 * lifecycle handlers they need. All handlers run on the main thread, invoked by the engine.
 *
 * <p>Contract: view fields hold only state tokens, injected collaborators and immutable
 * configuration — every per-player value lives in per-context state and is dropped when the
 * context closes. State factories are legal only in field initializers or the constructor;
 * after registration freezes the token table they throw {@link IllegalStateException}.
 */
public abstract class View {

    private final TokenTable tokenTable = new TokenTable();

    /**
     * Configures this view; called once per class at registration. The resulting config is
     * validated and frozen afterwards.
     *
     * @param config the mutable config builder
     */
    protected void onInit(@NotNull ViewConfigBuilder config) {
    }

    /**
     * Called once per open, before any container exists. May cancel the open (zero side
     * effects) or override title/rows for this open only.
     *
     * @param context the open context
     */
    protected void onOpen(@NotNull OpenContext context) {
    }

    /**
     * Declares this session's components; called once per open, after the container is
     * created and before the first paint.
     *
     * @param context the render context
     */
    protected void onFirstRender(@NotNull RenderContext context) {
    }

    /**
     * Called on every update pass; inspect {@link UpdateContext#trigger()} for the cause.
     *
     * @param context the update context
     */
    protected void onUpdate(@NotNull UpdateContext context) {
    }

    /**
     * View-level click fallback: runs after component handlers for top-container clicks
     * and receives every bottom-inventory click with {@code isPlayerInventory() == true}.
     *
     * @param context the click context
     */
    protected void onClick(@NotNull SlotClickContext context) {
    }

    /**
     * Called when the session tears down, with the matching {@code CloseReason}. Throwing
     * here is caught and logged; teardown always completes.
     *
     * @param context the close context
     */
    protected void onClose(@NotNull CloseContext context) {
    }

    /**
     * Declares a per-context mutable value seeded with a shared initial value. The initial
     * object is shared by every context and must be immutable; use
     * {@link #mutableState(Function)} for mutable initials such as collections.
     *
     * @param initialValue the shared initial value, possibly {@code null}
     * @param <T>          the value type
     * @return the state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> MutableState<T> mutableState(@Nullable T initialValue) {
        return new MutableStateImpl<>(this, tokenTable, context -> initialValue);
    }

    /**
     * Declares a per-context mutable value whose initial is computed per context on first
     * read, e.g. {@code mutableState(ctx -> new ArrayList<>())}.
     *
     * @param initialValue the per-context initial value factory
     * @param <T>          the value type
     * @return the state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> MutableState<T> mutableState(@NotNull Function<ViewContext, T> initialValue) {
        return new MutableStateImpl<>(this, tokenTable, initialValue);
    }

    /**
     * Declares a read-only value computed once per context on the first read, on the main
     * thread, and stored thereafter.
     *
     * @param computation the once-per-context computation
     * @param <T>         the value type
     * @return the state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> State<T> lazyState(@NotNull Function<ViewContext, T> computation) {
        return new LazyStateImpl<>(this, tokenTable, computation);
    }

    /**
     * Declares a mutable value bound from {@code ViewArguments} at open; the type is
     * validated at the open site. An absent key reads as {@code null} until set.
     *
     * @param key  the argument key bound at open
     * @param type the expected argument type
     * @param <T>  the value type
     * @return the state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> MutableState<T> initialState(@NotNull String key, @NotNull Class<T> type) {
        return new InitialStateImpl<>(this, tokenTable, key, type);
    }

    /**
     * Declares one atomic value per view singleton, shared by all viewers and writable
     * from any thread.
     *
     * @param initialValue the initial shared value, possibly {@code null}
     * @param <T>          the value type
     * @return the shared state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> SharedState<T> sharedState(@Nullable T initialValue) {
        return new SharedStateImpl<>(this, tokenTable, initialValue);
    }

    /**
     * Returns this view's token table; used by the engine to size per-session state
     * storage and to freeze token registration.
     *
     * @return the token registry of this view instance
     */
    @ApiStatus.Internal
    public final @NotNull TokenTable tokenTable() {
        return tokenTable;
    }
}
