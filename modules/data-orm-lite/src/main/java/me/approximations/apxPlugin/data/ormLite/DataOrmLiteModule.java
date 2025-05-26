package me.approximations.apxPlugin.data.ormLite;

import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.data.config.impl.HikariPersistenceUnitConfig;
import me.approximations.apxPlugin.data.ormLite.config.PersistenceConfig;
import me.approximations.apxPlugin.data.ormLite.config.registry.PersistenceConfigRegistry;
import me.approximations.apxPlugin.data.ormLite.repository.registry.OrmLiteRepositoryRegistry;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import me.approximations.apxPlugin.module.Module;

import javax.sql.DataSource;

@RequiredArgsConstructor
public class DataOrmLiteModule implements Module {
    private final PersistenceConfigRegistry persistenceConfigRegistry;
    private final OrmLiteRepositoryRegistry ormLiteRepositoryRegistry;
    private final DependencyManager dependencyManager;

    @Override
    public void initialize() throws Exception {
        final PersistenceConfig persistenceConfig = persistenceConfigRegistry.initialize();
        final DataSource dataSource = new HikariPersistenceUnitConfig().configure(
                persistenceConfig.getAddress(),
                persistenceConfig.getUsername(),
                persistenceConfig.getPassword()
        );

        ConnectionSource connectionSource = new DataSourceConnectionSource(dataSource, persistenceConfig.getAddress());
        dependencyManager.registerDependency(ConnectionSource.class, connectionSource);

        ormLiteRepositoryRegistry.initialize();
    }
}
