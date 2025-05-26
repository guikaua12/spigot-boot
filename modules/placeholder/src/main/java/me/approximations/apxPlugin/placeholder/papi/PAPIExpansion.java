package me.approximations.apxPlugin.placeholder.papi;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.di.annotations.Component;
import me.approximations.apxPlugin.placeholder.metadata.PlaceholderMetadata;
import me.approximations.apxPlugin.placeholder.registry.PlaceholderRegistry;
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
        final PlaceholderMetadata placeholder = placeholderRegistry.getPlaceholderMetadata(params);
        return placeholder != null && placeholder.isPlaceholderApi() ? placeholder.getValue(player, params) : null;
    }
}
