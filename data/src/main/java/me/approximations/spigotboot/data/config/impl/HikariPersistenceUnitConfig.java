package me.approximations.spigotboot.data.config.impl;

import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.Setter;
import me.approximations.spigotboot.data.config.PersistenceUnitConfig;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

@Setter
@Getter
public class HikariPersistenceUnitConfig implements PersistenceUnitConfig {
    private static final int MAXIMUM_POOL_SIZE = (Runtime.getRuntime().availableProcessors() * 2) + 1;
    private static final int MINIMUM_IDLE = Math.min(MAXIMUM_POOL_SIZE, 10);

    private static final long MAX_LIFETIME = TimeUnit.MINUTES.toMillis(30);
    private static final long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static final long LEAK_DETECTION_THRESHOLD = TimeUnit.SECONDS.toMillis(10);

    @Override
    public DataSource configure(String address, String username, String password) {
        HikariDataSource dataSource = new HikariDataSource();

        dataSource.setJdbcUrl(address);
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
        return dataSource;
    }
}