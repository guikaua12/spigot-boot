package me.approximations.spigotboot.data.ormLite.config;

import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import me.approximations.spigotboot.core.context.configuration.annotations.Bean;
import me.approximations.spigotboot.core.context.configuration.annotations.Configuration;
import me.approximations.spigotboot.data.config.impl.HikariPersistenceUnitConfig;
import me.approximations.spigotboot.data.ormLite.config.registry.PersistenceConfigRegistry;

import javax.sql.DataSource;
import java.sql.SQLException;

@Configuration
public class OrmLiteModuleConfiguration {
    @Bean
    public ConnectionSource connectionSource(PersistenceConfigRegistry persistenceConfigRegistry) throws SQLException {
        final PersistenceConfig persistenceConfig = persistenceConfigRegistry.initialize();

        final DataSource dataSource = new HikariPersistenceUnitConfig().configure(
                persistenceConfig.getAddress(),
                persistenceConfig.getUsername(),
                persistenceConfig.getPassword()
        );

        return new DataSourceConnectionSource(dataSource, persistenceConfig.getAddress());
    }
}
