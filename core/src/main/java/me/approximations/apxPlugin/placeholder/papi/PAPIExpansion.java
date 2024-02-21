package me.approximations.apxPlugin.placeholder.papi;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.placeholder.Placeholder;
import me.approximations.apxPlugin.placeholder.manager.PlaceholderManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringJoiner;

@RequiredArgsConstructor
public class PAPIExpansion extends PlaceholderExpansion {
    private final ApxPlugin plugin;
    private final PlaceholderManager placeholderManager;

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
        final Placeholder placeholder = placeholderManager.getPlaceholder(params);
        return placeholder != null && placeholder.shouldRegisterPAPI() ? placeholder.getValue(player) : null;
    }
}
