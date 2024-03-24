package me.approximations.apxPlugin.placeholder.manager;

import lombok.Getter;
import me.approximations.apxPlugin.placeholder.Placeholder;
import me.approximations.apxPlugin.placeholder.papi.PAPIExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class PlaceholderManager {
    public static final String PAPI_NAME = "PlaceholderAPI";

    private static final Map<String, Placeholder> placeholders = new HashMap<>();
    @Getter
    private static PAPIExpansion papiExpansion;

    public static void register(Placeholder placeholder) {
        placeholders.put(placeholder.getPlaceholder(), placeholder);
    }

    public static void unregister(Placeholder placeholder) {
        placeholders.remove(placeholder.getPlaceholder());
    }

    public static Placeholder getPlaceholder(String placeholder) {
        return placeholders.get(placeholder);
    }

    public static void clear() {
        placeholders.clear();
    }

    public static boolean init(Plugin plugin, Placeholder... placeholders) {
        if (!Bukkit.getPluginManager().isPluginEnabled(PAPI_NAME)) {
            plugin.getLogger().log(Level.INFO, "PlaceholderAPI not found, skipping registration of placeholders");
            return false;
        }

        papiExpansion = new PAPIExpansion(plugin);
        papiExpansion.register();

        for (final Placeholder placeholder : placeholders) {
            register(placeholder);
        }

        return true;
    }
}
