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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Mutable state token bound from {@code ViewArguments} at open: the open phase validates
 * the argument type and writes the value straight into the session's {@link StateStore}.
 * An absent key reads as {@code null} until set.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class InitialStateImpl<T> implements MutableState<T> {

    private final View owner;
    private final String key;
    private final Class<T> type;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner the view declaring the token
     * @param table the owner's token table
     * @param key   the {@code ViewArguments} key bound at open
     * @param type  the expected argument type, validated at the open site
     * @throws IllegalStateException when the table is already frozen
     */
    public InitialStateImpl(@NotNull View owner, @NotNull TokenTable table,
                            @NotNull String key, @NotNull Class<T> type) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(table, "table").register(this);
    }

    /**
     * Returns the table-assigned token id, the index into the per-session state store.
     *
     * @return the token id
     */
    public int id() {
        return id;
    }

    /**
     * Returns the {@code ViewArguments} key this token binds at open.
     *
     * @return the argument key
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * Returns the expected argument type, validated at the open site.
     *
     * @return the value type
     */
    public @NotNull Class<T> type() {
        return type;
    }

    @Override
    public @Nullable T get(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        StateStore store = storeFor(context);
        return type.cast(store.get(id));
    }

    @Override
    public void set(@NotNull ViewContext context, @Nullable T value) {
        Objects.requireNonNull(context, "context");
        StateStore store = storeFor(context);
        assertMainThread();
        store.set(id, value);
        store.markDirty(id);
    }

    @Override
    public void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn) {
        Objects.requireNonNull(fn, "fn");
        set(context, fn.apply(get(context)));
    }

    private StateStore storeFor(ViewContext context) {
        View contextOwner = ContextStateAccess.ownerOf(context);
        if (contextOwner != owner) {
            throw new StaleContextException("state token of " + owner.getClass().getName()
                    + " used with a context of " + contextOwner.getClass().getName());
        }
        if (!ContextStateAccess.isActive(context)) {
            throw new StaleContextException("context of " + owner.getClass().getName() + " is closed");
        }
        return ContextStateAccess.storeOf(context);
    }

    private static void assertMainThread() {
        // the server null-check keeps pure unit tests (no Bukkit) working on any thread
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MutableState.set/update must run on the main thread");
        }
    }
}
