package me.approximations.apxPlugin.testPlugin;

import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.testPlugin.listener.ChatListener;
import org.bukkit.Bukkit;

public class Main extends ApxPlugin {
    @Override
    protected void onPluginEnable() {
        Bukkit.getPluginManager().registerEvents(new ChatListener(), this);
    }

    public static ApxPlugin getPlugin() {
        return getPlugin(Main.class);
    }

}