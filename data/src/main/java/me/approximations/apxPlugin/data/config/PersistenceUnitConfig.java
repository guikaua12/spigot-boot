package me.approximations.apxPlugin.data.config;

import javax.sql.DataSource;

public interface PersistenceUnitConfig {
    DataSource configure(String address, String username, String password);
}
