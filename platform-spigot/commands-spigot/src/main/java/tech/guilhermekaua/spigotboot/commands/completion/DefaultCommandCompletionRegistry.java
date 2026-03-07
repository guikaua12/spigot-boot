package tech.guilhermekaua.spigotboot.commands.completion;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;

import java.util.*;

public class DefaultCommandCompletionRegistry implements CommandCompletionRegistry {
    private final Map<String, CommandCompletionProvider> providers = new LinkedHashMap<>();

    public DefaultCommandCompletionRegistry(List<CommandCompletionRegistryCustomizer> customizers) {
        registerBuiltIns();
        for (CommandCompletionRegistryCustomizer customizer : CommandSupport.sortBeans(customizers)) {
            customizer.customize(this);
        }
    }

    @Override
    public void register(String id, CommandCompletionProvider provider) {
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("Completion id cannot be blank.");
        }
        providers.put(normalizedId, provider);
    }

    @Override
    public CommandCompletionProvider resolve(String id) {
        if (id == null) {
            return null;
        }
        return providers.get(id.trim());
    }

    private void registerBuiltIns() {
        register("onlinePlayers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                values.add(player.getName());
            }
            return values;
        });
        register("offlinePlayers", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null) {
                    values.add(player.getName());
                }
            }
            return values;
        });
        register("worlds", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                values.add(world.getName());
            }
            return values;
        });
        register("materials", (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (Material material : Material.values()) {
                values.add(material.name().toLowerCase(Locale.ROOT));
            }
            return values;
        });
        register("booleans", (context, parameter, input) -> Arrays.asList("true", "false"));
    }
}
