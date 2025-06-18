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
package me.approximations.spigotboot.placeholder.papi;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.di.annotations.Component;
import me.approximations.spigotboot.placeholder.converter.TypeConverterManager;
import me.approximations.spigotboot.placeholder.metadata.PlaceholderMetadata;
import me.approximations.spigotboot.placeholder.registry.PlaceholderRegistry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringJoiner;

@Component
@RequiredArgsConstructor
public class PAPIExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final PlaceholderRegistry placeholderRegistry;
    private final TypeConverterManager typeConverterManager;

    @Override
    public @NotNull String getIdentifier() {
        return plugin.getName();
    }

    @Override
    public @NotNull String getAuthor() {
        final StringJoiner sj = new StringJoiner(", ");
        return plugin.getDescription().getAuthors().stream().reduce(sj, StringJoiner::add, StringJoiner::merge).toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        final PlaceholderMetadata placeholder = placeholderRegistry.findPlaceholderMetadata(params);
        return placeholder != null && placeholder.isPlaceholderApi() ? placeholder.getValue(player, params, typeConverterManager) : null;
    }
}
