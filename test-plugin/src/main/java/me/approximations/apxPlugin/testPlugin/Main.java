package me.approximations.apxPlugin.testPlugin;

import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.commands.CommandManager;
import me.approximations.apxPlugin.di.annotations.Inject;
import me.approximations.apxPlugin.testPlugin.command.ApxPluginCommands;
import me.approximations.apxPlugin.testPlugin.repositories.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class Main extends ApxPlugin {
    @Inject
    private UserRepository userRepository;

    @Override
    protected void onPluginEnable() {
        System.out.println(userRepository.findAll());

//        final BungeeChannel bungeeChannel = getDependencyManager().getDependency(BungeeChannel.class);
//        bungeeChannel.init();
//
//        bungeeChannel.subscribe("test", message -> {
//            System.out.println("[Subscriber] Received on channel `test` message: " + message.getBody());
//            message.respond("Response from any server!");
//        });

        final CommandManager commandManager = new CommandManager(this);
        getDependencyManager().registerDependency(CommandManager.class, commandManager);

        commandManager.registerCompletion("players", context ->
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toSet())
        );

        commandManager.registerCommand(new ApxPluginCommands());
    }

    public static ApxPlugin getPlugin() {
        return getPlugin(Main.class);
    }
}