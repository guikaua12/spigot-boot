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
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed form of {@link ViewConfig#layout()}: maps each named layout character to its
 * container slots in row-major order; {@code ' '} marks an unnamed position and is skipped.
 */
@ApiStatus.Internal
public final class ResolvedLayout {

    private static final int[] NO_SLOTS = new int[0];

    private final int rows;
    private final Map<Character, int[]> slotsByChar;

    private ResolvedLayout(int rows, @NotNull Map<Character, int[]> slotsByChar) {
        this.rows = rows;
        this.slotsByChar = slotsByChar;
    }

    /**
     * Parses the layout rows of the given config; layout shape was already validated at build time.
     *
     * @param config the effective view config
     * @return the resolved layout; an empty mapping when {@code config.layout()} is empty
     */
    public static @NotNull ResolvedLayout resolve(@NotNull ViewConfig config) {
        List<String> layout = config.layout();
        if (layout.isEmpty()) {
            return new ResolvedLayout(config.rows(), Collections.<Character, int[]>emptyMap());
        }

        Map<Character, List<Integer>> collected = new LinkedHashMap<>();
        for (int row = 0; row < layout.size(); row++) {
            String line = layout.get(row);
            for (int column = 0; column < line.length(); column++) {
                char character = line.charAt(column);
                if (character == ' ') {
                    continue;
                }

                List<Integer> positions = collected.get(character);
                if (positions == null) {
                    positions = new ArrayList<>();
                    collected.put(character, positions);
                }
                positions.add(row * Layout.ROW_WIDTH + column);
            }
        }

        Map<Character, int[]> slotsByChar = new HashMap<>();
        for (Map.Entry<Character, List<Integer>> entry : collected.entrySet()) {
            List<Integer> positions = entry.getValue();
            int[] slots = new int[positions.size()];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = positions.get(i);
            }
            slotsByChar.put(entry.getKey(), slots);
        }
        return new ResolvedLayout(layout.size(), slotsByChar);
    }

    /**
     * @return the layout row count, or the config's resolved rows when no layout was declared
     */
    public int rows() {
        return rows;
    }

    /**
     * @param c the layout character to test
     * @return true when the character names at least one slot
     */
    public boolean hasChar(char c) {
        return slotsByChar.containsKey(c);
    }

    /**
     * @param c the layout character to look up
     * @return the character's slots in row-major order; an empty array when absent
     */
    public @NotNull int[] slotsOf(char c) {
        int[] slots = slotsByChar.get(c);
        return slots != null ? slots.clone() : NO_SLOTS;
    }
}
