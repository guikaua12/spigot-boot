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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.List;

/**
 * Static lookup helpers over a session's pagination bindings. Bindings live in the owning
 * token's {@code StateStore} slot (created by the open phase), so lookups walk the view's
 * token table and read each pagination token's slot.
 */
@ApiStatus.Internal
public final class PaginationBindings {

    private PaginationBindings() {
    }

    /**
     * Returns the pagination bindings of a session in token-declaration order.
     *
     * @param session the session to inspect
     * @return the bindings created for the session; empty when the view declares no
     *         pagination tokens or the open phase has not seeded them yet
     */
    public static @NotNull List<PaginationBinding> of(@NotNull ViewSession session) {
        List<PaginationBinding> bindings = new ArrayList<>();
        for (StateToken token : session.registered().instance().tokenTable().tokens()) {
            if (!(token instanceof PaginationImpl)) {
                continue;
            }
            Object stored = session.stateStore().get(((PaginationImpl<?>) token).tokenId());
            if (stored != null) {
                bindings.add((PaginationBinding) stored);
            }
        }
        return bindings;
    }

    /**
     * Looks up the pagination element component currently occupying a slot.
     *
     * @param session the session to inspect
     * @param slot    the container slot
     * @return the element component at the slot, or {@code null} when no binding owns it
     */
    public static @Nullable ComponentInstance componentAt(@NotNull ViewSession session, int slot) {
        for (PaginationBinding binding : of(session)) {
            ComponentInstance component = binding.componentAt(slot);
            if (component != null) {
                return component;
            }
        }
        return null;
    }
}
