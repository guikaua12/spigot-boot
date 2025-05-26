package me.approximations.apxPlugin.testPlugin.configuration;

import org.h2.Driver;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;

public class PersistenceConfig implements me.approximations.apxPlugin.persistence.jpa.config.PersistenceConfig {
    @Override
    public String getPersistenceUnitName() {
        return "test-unit";
    }

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

    @Override
    public Class<? extends Driver> getJdbcDriver() {
        return Driver.class;
    }

    @Override
    public boolean showSql() {
        return true;
    }

    @Override
    public Class<? extends Dialect> getDialect() {
        return H2Dialect.class;
    }

    @Override
    public String getDdlAuto() {
        return "update";
    }
}
