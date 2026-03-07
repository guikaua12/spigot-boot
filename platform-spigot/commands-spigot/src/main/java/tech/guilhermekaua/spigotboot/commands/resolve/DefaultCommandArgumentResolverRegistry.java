package tech.guilhermekaua.spigotboot.commands.resolve;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.*;

public class DefaultCommandArgumentResolverRegistry implements CommandArgumentResolverRegistry {
    private final List<CommandArgumentResolver<?>> resolvers = new ArrayList<>();

    public DefaultCommandArgumentResolverRegistry(List<CommandArgumentResolver<?>> customResolvers,
                                                  List<CommandArgumentResolverRegistryCustomizer> customizers) {
        registerBuiltIns();
        for (CommandArgumentResolver<?> resolver : CommandSupport.sortBeans(customResolvers)) {
            register(resolver);
        }
        for (CommandArgumentResolverRegistryCustomizer customizer : CommandSupport.sortBeans(customizers)) {
            customizer.customize(this);
        }
    }

    @Override
    public void register(CommandArgumentResolver<?> resolver) {
        if (resolver == null) {
            return;
        }
        resolvers.add(resolver);
        List<CommandArgumentResolver<?>> sorted = CommandSupport.sortBeans(resolvers);
        resolvers.clear();
        resolvers.addAll(sorted);
    }

    @Override
    public CommandArgumentResolver<?> resolve(CommandParameterMetadata parameter) {
        for (CommandArgumentResolver<?> resolver : resolvers) {
            if (resolver.supports(parameter)) {
                return resolver;
            }
        }
        return null;
    }

    private void registerBuiltIns() {
        register(new StringArgumentResolver());
        register(new BooleanArgumentResolver());
        register(new NumericArgumentResolver());
        register(new EnumArgumentResolver());
        register(new UuidArgumentResolver());
        register(new PlayerArgumentResolver());
        register(new OfflinePlayerArgumentResolver());
        register(new WorldArgumentResolver());
        register(new MaterialArgumentResolver());
    }

    private abstract static class BuiltInResolver<T> implements CommandArgumentResolver<T>, Ordered {
        @Override
        public int getOrder() {
            return 1000;
        }
    }

    private static final class StringArgumentResolver extends BuiltInResolver<String> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return String.class.equals(parameter.getValueType());
        }

        @Override
        public String resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            return input;
        }
    }

    private static final class BooleanArgumentResolver extends BuiltInResolver<Boolean> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            Class<?> type = parameter.getValueType();
            return Boolean.class.equals(type) || boolean.class.equals(type);
        }

        @Override
        public Boolean resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            String normalized = input.toLowerCase(Locale.ROOT);
            if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
                return true;
            }
            if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
                return false;
            }
            throw new IllegalArgumentException("Invalid boolean: " + input);
        }

        @Override
        public CommandCompletionProvider defaultCompletionProvider() {
            return (context, parameter, input) -> Arrays.asList("true", "false");
        }
    }

    private static final class NumericArgumentResolver extends BuiltInResolver<Number> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            Class<?> type = parameter.getValueType();
            return byte.class.equals(type) || Byte.class.equals(type)
                    || short.class.equals(type) || Short.class.equals(type)
                    || int.class.equals(type) || Integer.class.equals(type)
                    || long.class.equals(type) || Long.class.equals(type)
                    || float.class.equals(type) || Float.class.equals(type)
                    || double.class.equals(type) || Double.class.equals(type);
        }

        @Override
        public Number resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            Class<?> type = parameter.getValueType();
            if (byte.class.equals(type) || Byte.class.equals(type)) {
                return Byte.parseByte(input);
            }
            if (short.class.equals(type) || Short.class.equals(type)) {
                return Short.parseShort(input);
            }
            if (int.class.equals(type) || Integer.class.equals(type)) {
                return Integer.parseInt(input);
            }
            if (long.class.equals(type) || Long.class.equals(type)) {
                return Long.parseLong(input);
            }
            if (float.class.equals(type) || Float.class.equals(type)) {
                return Float.parseFloat(input);
            }
            if (double.class.equals(type) || Double.class.equals(type)) {
                return Double.parseDouble(input);
            }
            throw new IllegalArgumentException("Unsupported numeric type: " + type.getName());
        }
    }

    private static final class EnumArgumentResolver extends BuiltInResolver<Enum<?>> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return parameter.getValueType().isEnum();
        }

        @SuppressWarnings({"rawtypes"})
        @Override
        public Enum<?> resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            for (Object constant : parameter.getValueType().getEnumConstants()) {
                Enum value = (Enum) constant;
                if (value.name().equalsIgnoreCase(input)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Invalid enum constant: " + input);
        }

        @Override
        public CommandCompletionProvider defaultCompletionProvider() {
            return (context, parameter, input) -> {
                List<String> values = new ArrayList<>();
                for (Object constant : parameter.getValueType().getEnumConstants()) {
                    values.add(((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
                }
                return values;
            };
        }
    }

    private static final class UuidArgumentResolver extends BuiltInResolver<UUID> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return UUID.class.equals(parameter.getValueType());
        }

        @Override
        public UUID resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            return UUID.fromString(input);
        }
    }

    private static final class PlayerArgumentResolver extends BuiltInResolver<Player> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return Player.class.equals(parameter.getValueType());
        }

        @Override
        public Player resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            Player player = Bukkit.getPlayerExact(input);
            if (player == null) {
                player = Bukkit.getPlayer(input);
            }
            if (player == null) {
                throw new IllegalArgumentException("Player not found: " + input);
            }
            return player;
        }

        @Override
        public CommandCompletionProvider defaultCompletionProvider() {
            return (context, parameter, input) -> {
                List<String> values = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    values.add(player.getName());
                }
                return values;
            };
        }
    }

    private static final class OfflinePlayerArgumentResolver extends BuiltInResolver<OfflinePlayer> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return OfflinePlayer.class.equals(parameter.getValueType()) && !Player.class.equals(parameter.getValueType());
        }

        @Override
        public OfflinePlayer resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            Player online = Bukkit.getPlayerExact(input);
            if (online != null) {
                return online;
            }

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null && player.getName().equalsIgnoreCase(input)) {
                    return player;
                }
            }

            OfflinePlayer fallback = Bukkit.getOfflinePlayer(input);
            if (fallback.getName() == null && !fallback.hasPlayedBefore()) {
                throw new IllegalArgumentException("Offline player not found: " + input);
            }
            return fallback;
        }

        @Override
        public CommandCompletionProvider defaultCompletionProvider() {
            return (context, parameter, input) -> {
                List<String> values = new ArrayList<>();
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    if (player.getName() != null) {
                        values.add(player.getName());
                    }
                }
                return values;
            };
        }
    }

    private static final class WorldArgumentResolver extends BuiltInResolver<World> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return World.class.equals(parameter.getValueType());
        }

        @Override
        public World resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            World world = Bukkit.getWorld(input);
            if (world == null) {
                throw new IllegalArgumentException("World not found: " + input);
            }
            return world;
        }

        @Override
        public CommandCompletionProvider defaultCompletionProvider() {
            return (context, parameter, input) -> {
                List<String> values = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    values.add(world.getName());
                }
                return values;
            };
        }
    }

    private static final class MaterialArgumentResolver extends BuiltInResolver<Material> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return Material.class.equals(parameter.getValueType());
        }

        @Override
        public Material resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            Material material = Material.matchMaterial(input);
            if (material == null) {
                throw new IllegalArgumentException("Material not found: " + input);
            }
            return material;
        }

        @Override
        public CommandCompletionProvider defaultCompletionProvider() {
            return (context, parameter, input) -> {
                List<String> values = new ArrayList<>();
                for (Material material : Material.values()) {
                    values.add(material.name().toLowerCase(Locale.ROOT));
                }
                return values;
            };
        }
    }
}
