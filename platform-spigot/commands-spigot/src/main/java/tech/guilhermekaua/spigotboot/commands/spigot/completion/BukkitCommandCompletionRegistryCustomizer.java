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
            Player senderPlayer = context.getSender().unwrap(Player.class).orElse(null);
            String lowerInput = input != null ? input.toLowerCase(Locale.ROOT) : "";
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (senderPlayer != null && !senderPlayer.canSee(player)) {
                    continue;
                }
                if (!lowerInput.isEmpty() && !player.getName().toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                    continue;
                }
                values.add(player.getName());
            }
            return values;
        });
        registry.register("offlinePlayers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            String lowerInput = input != null ? input.toLowerCase(Locale.ROOT) : "";
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                String name = player.getName();
                if (name == null) {
                    continue;
                }
                if (!lowerInput.isEmpty() && !name.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                    continue;
                }
                values.add(name);
                if (values.size() >= 20) {
                    break;
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
            // Enum-typed on purpose: the 1.8.8 sniffer signature lacks JDK supertypes, so
            // name() must resolve via the java.* ignore (see root pom)
            for (Enum<?> material : Material.values()) {
                values.add(material.name().toLowerCase(Locale.ROOT));
            }
            return values;
        });
    }
}
