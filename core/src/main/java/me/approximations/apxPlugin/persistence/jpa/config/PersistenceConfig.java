package me.approximations.apxPlugin.persistence.jpa.config;

import org.hibernate.dialect.Dialect;

import java.sql.Driver;

public interface PersistenceConfig {
    String getPersistenceUnitName();

    String getAddress();

    String getUsername();

    String getPassword();

    Class<? extends Driver> getJdbcDriver();

    boolean showSql();

    Class<? extends Dialect> getDialect();
}
