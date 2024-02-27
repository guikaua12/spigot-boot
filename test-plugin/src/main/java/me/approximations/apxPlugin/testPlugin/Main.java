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

        getDependencyManager().getDependency(BungeeChannel.class).init();
    }

    public static ApxPlugin getPlugin() {
        return getPlugin(Main.class);
    }
}