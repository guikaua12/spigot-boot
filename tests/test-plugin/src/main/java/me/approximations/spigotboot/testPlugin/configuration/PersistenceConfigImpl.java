package me.approximations.spigotboot.testPlugin.configuration;

import me.approximations.spigotboot.data.ormLite.config.PersistenceConfig;

public class PersistenceConfigImpl implements PersistenceConfig {
    @Override
    public String getAddress() {
        return "jdbc:h2:mem:test";
    }

    @Override
    public String getUsername() {
        return "sa";
    }

    @Override
    public String getPassword() {
        return "";
    }
}
