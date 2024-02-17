package me.approximations.apxPlugin.testPlugin.configuration;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQL8Dialect;

import java.sql.Driver;

public class PersistenceConfig implements me.approximations.apxPlugin.persistence.jpa.config.PersistenceConfig {
    @Override
    public String getPersistenceUnitName() {
        return "test-unit";
    }

    @Override
    public String getAddress() {
        return "jdbc:mysql://localhost:3306/test";
    }

    @Override
    public String getUsername() {
        return "root";
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public Class<? extends Driver> getJdbcDriver() {
        try {
            return (Class<? extends Driver>) Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean showSql() {
        return true;
    }

    @Override
    public Class<? extends Dialect> getDialect() {
        return MySQL8Dialect.class;
    }
}
