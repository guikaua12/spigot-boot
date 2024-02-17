package me.approximations.apxPlugin.testPlugin;

import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.dependencyInjection.annotations.Inject;
import me.approximations.apxPlugin.testPlugin.repositories.UserRepository;

public class Main extends ApxPlugin {
    @Inject
    private UserRepository userRepository;

    @Override
    protected void onPluginEnable() {
        System.out.println(userRepository.findAll());
    }

    public static ApxPlugin getPlugin() {
        return getPlugin(Main.class);
    }

}