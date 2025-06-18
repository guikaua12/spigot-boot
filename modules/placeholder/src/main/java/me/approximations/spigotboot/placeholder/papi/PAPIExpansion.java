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
