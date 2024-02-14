package me.approximations.apxPlugin.testPlugin;

import me.approximations.apxPlugin.ApxPlugin;

public class Main extends ApxPlugin {
    @Override
    protected void onPluginEnable() {
    }

    public static ApxPlugin getPlugin() {
        return getPlugin(Main.class);
    }

}