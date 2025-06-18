package me.approximations.spigotboot.data.config;

import javax.sql.DataSource;

public interface PersistenceUnitConfig {
    DataSource configure(String address, String username, String password);
}
