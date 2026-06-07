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
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-session state storage: an array indexed by token id plus the set of dirty token ids
 * awaiting the next coalesced flush. Owned by the session and dropped with it.
 */
@ApiStatus.Internal
public final class StateStore {

    private final Object[] values;
    private final Set<Integer> dirty = new LinkedHashSet<>();

    /**
     * Creates storage sized for a view's token table.
     *
     * @param size the token count of the owning view
     */
    public StateStore(int size) {
        this.values = new Object[size];
    }

    /**
     * Returns the stored value for a token id.
     *
     * @param id the token id
     * @return the stored value, or {@code null} when nothing was stored
     */
    public @Nullable Object get(int id) {
        return values[id];
    }

    /**
     * Stores a value for a token id, replacing any previous value.
     *
     * @param id    the token id
     * @param value the value to store, possibly {@code null}
     */
    public void set(int id, @Nullable Object value) {
        values[id] = value;
    }

    /**
     * Marks a token id dirty for the next flush; duplicate marks coalesce.
     *
     * @param id the token id
     */
    public void markDirty(int id) {
        dirty.add(id);
    }

    /**
     * Returns the dirty token ids and clears the dirty set.
     *
     * @return the ids marked dirty since the last drain
     */
    public @NotNull Set<Integer> drainDirty() {
        Set<Integer> drained = new LinkedHashSet<>(dirty);
        dirty.clear();
        return drained;
    }

    /**
     * Returns whether any token id is currently marked dirty.
     *
     * @return {@code true} when a flush is pending
     */
    public boolean hasDirty() {
        return !dirty.isEmpty();
    }
}
