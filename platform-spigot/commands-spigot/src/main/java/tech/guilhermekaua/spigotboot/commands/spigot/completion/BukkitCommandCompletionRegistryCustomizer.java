package tech.guilhermekaua.spigotboot.commands.spigot.completion;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistryCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BukkitCommandCompletionRegistryCustomizer implements CommandCompletionRegistryCustomizer {
    @Override
    public void customize(CommandCompletionRegistry registry) {
        registry.register("onlinePlayers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                values.add(player.getName());
            }
            return values;
        });
        registry.register("offlinePlayers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null) {
                    values.add(player.getName());
                }
            }
            return values;
        });
        registry.register("worlds", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                values.add(world.getName());
            }
            return values;
        });
        registry.register("materials", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (Material material : Material.values()) {
                values.add(material.name().toLowerCase(Locale.ROOT));
            }
            return values;
        });
    }
}
