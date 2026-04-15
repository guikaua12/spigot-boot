/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.versions.runtime.network.metadata;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One equipment slot snapshot prepared for network synchronization.
 *
 * @since 2.0.2
 */
public final class EquipmentEntry {
    private final String slot;
    private final Object item;

    /**
     * Creates one equipment entry.
     *
     * @param slot the logical slot identifier
     * @param item the slot item payload
     */
    public EquipmentEntry(@NotNull String slot, @Nullable Object item) {
        this.slot = Objects.requireNonNull(slot, "slot cannot be null");
        this.item = item;
    }

    /**
     * Returns the logical slot identifier.
     *
     * @return the slot identifier
     */
    public @NotNull String slot() {
        return slot;
    }

    /**
     * Returns the slot item payload.
     *
     * @return the slot item payload
     */
    public @Nullable Object item() {
        return item;
    }
}
