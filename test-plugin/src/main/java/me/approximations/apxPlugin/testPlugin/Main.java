package me.approximations.apxPlugin.testPlugin;

import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.di.annotations.Inject;
import me.approximations.apxPlugin.messaging.bungee.BungeeChannel;
import me.approximations.apxPlugin.testPlugin.repositories.UserRepository;

public class Main extends ApxPlugin {
    @Inject
    private UserRepository userRepository;

    @Override
    protected void onPluginEnable() {
        System.out.println(userRepository.findAll());

        final BungeeChannel bungeeChannel = getDependencyManager().getDependency(BungeeChannel.class);
        bungeeChannel.init();

        bungeeChannel.subscribe("test", message -> {
            System.out.println("[Subscriber] Received on channel `test` message: " + message.getBody());
            message.respond("Response from any server!");
        });
    }

    public static ApxPlugin getPlugin() {
        return getPlugin(Main.class);
    }
}