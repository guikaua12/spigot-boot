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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Per-view registry of state tokens. Tokens register during view construction and receive
 * sequential ids; registration is frozen once the view is registered.
 */
@ApiStatus.Internal
public final class TokenTable {

    private final List<StateToken> tokens = new ArrayList<>();
    private boolean frozen;

    /**
     * Registers a token and assigns its id.
     *
     * @param token the token to register
     * @return the assigned sequential id, starting at 0
     * @throws IllegalStateException when the table is already frozen
     */
    public int register(@NotNull StateToken token) {
        Objects.requireNonNull(token, "token");
        if (frozen) {
            throw new IllegalStateException("state factories are only legal in field initializers or the constructor; "
                    + "the token table is frozen once the view is registered");
        }
        tokens.add(token);
        return tokens.size() - 1;
    }

    /**
     * Freezes this table; further {@link #register(StateToken)} calls throw.
     */
    public void freeze() {
        frozen = true;
    }

    /**
     * Returns whether {@link #freeze()} was called.
     *
     * @return {@code true} once frozen
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Returns the number of registered tokens.
     *
     * @return the token count
     */
    public int size() {
        return tokens.size();
    }

    /**
     * Returns the registered tokens in id order.
     *
     * @return an unmodifiable view of the token list
     */
    public @NotNull List<StateToken> tokens() {
        return Collections.unmodifiableList(tokens);
    }
}
