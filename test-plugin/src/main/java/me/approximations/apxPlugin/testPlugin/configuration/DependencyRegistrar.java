package me.approximations.apxPlugin.testPlugin.configuration;


import me.approximations.apxPlugin.di.annotations.DependencyRegister;
import me.approximations.apxPlugin.messaging.bungee.BungeeChannel;

@DependencyRegister
public class DependencyRegistrar {
    public BungeeChannel getBungeeChannel() {
        return new BungeeChannel();
    }
}
