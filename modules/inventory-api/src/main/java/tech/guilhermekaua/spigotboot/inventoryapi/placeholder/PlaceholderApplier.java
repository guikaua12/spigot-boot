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
package tech.guilhermekaua.spigotboot.inventoryapi.placeholder;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves placeholder tokens in user-facing text (item display names, lore lines, titles).
 *
 * <p>The default implementation, {@link PapiPlaceholderApplier}, delegates to PlaceholderAPI
 * when the plugin is loaded. When PlaceholderAPI is absent the auto-configuration installs
 * {@link NoopPlaceholderApplier}, which returns the input unchanged. Users can override either
 * behavior by registering their own bean.
 */
@FunctionalInterface
public interface PlaceholderApplier {
    /**
     * Applies placeholders to a single text fragment.
     *
     * @param player the viewer whose context should resolve player-scoped placeholders;
     *               may be {@code null} when no specific player is in scope
     * @param text   the text to process; may be {@code null}
     * @return the processed text, or the original input if {@code text} is {@code null}
     */
    String apply(@Nullable Player player, @Nullable String text);

    /**
     * Applies placeholders to every entry of a list, preserving order and tolerating
     * {@code null} entries.
     *
     * @param player the viewer whose context should resolve player-scoped placeholders
     * @param texts  the lines to process; may be {@code null}
     * @return a fresh list with placeholders resolved, or {@code null} if {@code texts} is {@code null}
     */
    default List<String> applyAll(@Nullable Player player, @Nullable List<String> texts) {
        if (texts == null) {
            return null;
        }

        List<String> processed = new ArrayList<>(texts.size());
        for (String line : texts) {
            processed.add(apply(player, line));
        }
        return processed;
    }
}
