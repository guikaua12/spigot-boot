package me.approximations.apxPlugin.testPlugin.configuration;

import me.approximations.apxPlugin.data.ormLite.config.PersistenceConfig;

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
