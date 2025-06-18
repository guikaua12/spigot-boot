package me.approximations.spigotboot.placeholder;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.ApxPlugin;
import me.approximations.spigotboot.core.module.Module;
import me.approximations.spigotboot.core.module.annotations.ConditionalOnClass;
import me.approximations.spigotboot.placeholder.registry.PlaceholderRegistry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;

@RequiredArgsConstructor
@ConditionalOnClass(value = PlaceholderExpansion.class, message = "PlaceholderAPI not found, skipping registration of placeholders.")
public class PlaceholderModule implements Module {
    private static final String PAPI_NAME = "PlaceholderAPI";

    private final ApxPlugin plugin;
    private final PlaceholderRegistry placeholderRegistry;

    @Override
    public void initialize() throws Exception {
        plugin.getLogger().info("Initializing Placeholder Module...");

        if (!Bukkit.getPluginManager().isPluginEnabled(PAPI_NAME)) {
            plugin.getLogger().info("PlaceholderAPI not found, skipping registration of placeholders.");
            return;
        }

        placeholderRegistry.initialize();
        plugin.addDisableEntry(() -> {
            plugin.getLogger().info("Unregistering PlaceholderAPI expansion...");
            if (Bukkit.getPluginManager().isPluginEnabled(PAPI_NAME)) {
                placeholderRegistry.unregister();
            }
        });
    }
}
