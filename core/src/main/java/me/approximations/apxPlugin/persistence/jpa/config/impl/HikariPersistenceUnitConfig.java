package me.approximations.apxPlugin.persistence.jpa.config.impl;

import com.zaxxer.hikari.HikariDataSource;
import lombok.Setter;
import me.approximations.apxPlugin.persistence.jpa.config.PersistenceConfig;
import me.approximations.apxPlugin.persistence.jpa.config.PersistenceUnitConfig;
import org.hibernate.dialect.Dialect;

import javax.persistence.spi.PersistenceUnitInfo;
import javax.sql.DataSource;
import java.sql.Driver;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Setter
public class HikariPersistenceUnitConfig extends PersistenceUnitConfig implements PersistenceUnitInfo {
    private static final int MAXIMUM_POOL_SIZE = (Runtime.getRuntime().availableProcessors() * 2) + 1;
    private static final int MINIMUM_IDLE = Math.min(MAXIMUM_POOL_SIZE, 10);

    private static final long MAX_LIFETIME = TimeUnit.MINUTES.toMillis(30);
    private static final long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static final long LEAK_DETECTION_THRESHOLD = TimeUnit.SECONDS.toMillis(10);

    private final HikariDataSource dataSource = new HikariDataSource();

    public HikariPersistenceUnitConfig(String persistenceUnitName, String address, String username, String password, Class<? extends Driver> jdbcDriver, boolean showSql, List<Class<?>> entitiesClasses, Class<? extends Dialect> dialect) {
        super(persistenceUnitName, address, username, entitiesClasses, password, jdbcDriver, dialect, showSql);

        dataSource.setJdbcUrl(address);
        dataSource.setDriverClassName(jdbcDriver.getName());

        dataSource.setUsername(username);
        dataSource.setPassword(password);

        dataSource.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        dataSource.setMinimumIdle(MINIMUM_IDLE);

        dataSource.setMaxLifetime(MAX_LIFETIME);
        dataSource.setConnectionTimeout(CONNECTION_TIMEOUT);
        dataSource.setLeakDetectionThreshold(LEAK_DETECTION_THRESHOLD);

        dataSource.addDataSourceProperty("useUnicode", true);
        dataSource.addDataSourceProperty("characterEncoding", "utf8");

        dataSource.addDataSourceProperty("cachePrepStmts", "true");
        dataSource.addDataSourceProperty("prepStmtCacheSize", "250");
        dataSource.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource.addDataSourceProperty("useServerPrepStmts", "true");
        dataSource.addDataSourceProperty("useLocalSessionState", "true");
        dataSource.addDataSourceProperty("rewriteBatchedStatements", "true");
        dataSource.addDataSourceProperty("cacheResultSetMetadata", "true");
        dataSource.addDataSourceProperty("cacheServerConfiguration", "true");
        dataSource.addDataSourceProperty("elideSetAutoCommits", "true");
        dataSource.addDataSourceProperty("maintainTimeStats", "false");
        dataSource.addDataSourceProperty("alwaysSendSetIsolation", "false");
        dataSource.addDataSourceProperty("cacheCallableStmts", "true");

        dataSource.addDataSourceProperty("socketTimeout", String.valueOf(TimeUnit.SECONDS.toMillis(30)));
    }

    public HikariPersistenceUnitConfig(PersistenceConfig persistenceConfig, List<Class<?>> entitiesClasses) {
        this(persistenceConfig.getPersistenceUnitName(), persistenceConfig.getAddress(), persistenceConfig.getUsername(),
                persistenceConfig.getPassword(), persistenceConfig.getJdbcDriver(), persistenceConfig.showSql(), entitiesClasses,
                persistenceConfig.getDialect()
        );
    }

    @Override
    public String getPersistenceUnitName() {
        return "jpa-example";
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return dataSource;
    }

    @Override
    public Properties getProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.connection.url", address);
        properties.setProperty("hibernate.connection.username", username);
        properties.setProperty("hibernate.connection.password", password);
        properties.setProperty("hibernate.connection.driver_class", jdbcDriver.getName());
        properties.setProperty("hibernate.dialect", dialect.getName());
        properties.setProperty("hibernate.show_sql", String.valueOf(showSql));
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        return properties;
    }
}