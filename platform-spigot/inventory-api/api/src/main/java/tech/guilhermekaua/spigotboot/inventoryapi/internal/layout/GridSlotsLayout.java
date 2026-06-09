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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.layout;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Grid-parsed {@link Layout} backing {@link Layout#ofGrid}: {@code ' '} is empty, every
 * other character is a fill slot ordered by character code point (Unicode value), then occurrence.
 */
@ApiStatus.Internal
public final class GridSlotsLayout implements Layout {

    private final List<Integer> slots;

    /**
     * Parses the given grid rows into an ordered fill sequence.
     *
     * @param rows the grid rows, each exactly {@link Layout#ROW_WIDTH} characters wide
     * @throws IllegalArgumentException if a row is not exactly {@link Layout#ROW_WIDTH} characters wide
     */
    public GridSlotsLayout(@NotNull String... rows) {
        List<int[]> named = new ArrayList<>();

        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            if (line == null) {
                throw new IllegalArgumentException("row " + row + " must not be null");
            }
            if (line.length() != ROW_WIDTH) {
                throw new IllegalArgumentException(
                        "layout row " + row + " must be exactly " + ROW_WIDTH
                                + " characters wide, but was " + line.length()
                );
            }

            for (int column = 0; column < ROW_WIDTH; column++) {
                char character = line.charAt(column);
                if (character != ' ') {
                    named.add(new int[]{character, row * ROW_WIDTH + column});
                }
            }
        }

        // stable sort groups occurrences of the same char while keeping row-major order within a group
        named.sort(Comparator.comparingInt((int[] entry) -> entry[0]));

        List<Integer> ordered = new ArrayList<>(named.size());
        for (int[] entry : named) {
            ordered.add(entry[1]);
        }
        this.slots = Collections.unmodifiableList(ordered);
    }

    @Override
    public @NotNull List<Integer> slots() {
        return slots;
    }
}
