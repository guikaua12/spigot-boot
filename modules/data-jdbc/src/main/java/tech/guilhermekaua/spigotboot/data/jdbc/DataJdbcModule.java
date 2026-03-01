/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.data.jdbc;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.data.config.PersistenceConfig;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Table;
import tech.guilhermekaua.spigotboot.data.jdbc.config.DataJdbcConfiguration;
import tech.guilhermekaua.spigotboot.data.jdbc.config.JdbcSchemaOptions;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.BuiltInConverters;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.ConverterScanner;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.ddl.DdlGenerator;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.DialectResolver;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataParser;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.registry.JdbcRepositoryRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.transaction.JdbcTransactionManager;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DataJdbcModule implements Module {
    private static final Logger LOGGER = Logger.getLogger(DataJdbcModule.class.getName());

    @Inject
    private JdbcRepositoryRegistry repositoryRegistry;

    @Override
    public void onInitialize(Context context) throws Exception {
        String basePackage = context.getPlugin().getMainClass().getPackage().getName();

        PersistenceConfig persistenceConfig = discoverPersistenceConfig(context, basePackage);
        Dialect dialect = resolveOrRegisterDialect(context, persistenceConfig);
        DataSource dataSource = resolveOrRegisterDataSource(context, persistenceConfig, dialect);
        registerDataSourceShutdownHook(context, dataSource);
        TypeConverterRegistry converterRegistry = resolveOrRegisterConverterRegistry(context, basePackage);
        EntityMetadataRegistry metadataRegistry = resolveOrRegisterMetadataRegistry(context, converterRegistry);
        ConnectionProvider connectionProvider = resolveOrRegisterConnectionProvider(context, dataSource);
        resolveOrRegisterTransactionManager(context, dataSource);
        JdbcSchemaOptions schemaOptions = resolveOrRegisterSchemaOptions(context);

        if (schemaOptions.isAutoDdlEnabled()) {
            runAutoDdl(basePackage, metadataRegistry, dialect, connectionProvider);
        }

        repositoryRegistry.initialize(context, connectionProvider, dialect, metadataRegistry);
    }

    private Dialect resolveOrRegisterDialect(Context context, PersistenceConfig persistenceConfig) {
        Dialect dialect = context.getBean(Dialect.class);
        if (dialect != null) {
            return dialect;
        }

        Dialect resolvedDialect = DialectResolver.resolve(persistenceConfig.getAddress());
        context.registerBean(resolvedDialect);
        return resolvedDialect;
    }

    private DataSource resolveOrRegisterDataSource(Context context, PersistenceConfig persistenceConfig, Dialect dialect) {
        DataSource dataSource = context.getBean(DataSource.class);
        if (dataSource != null) {
            return dataSource;
        }

        DataSource createdDataSource = new DataJdbcConfiguration().createDataSource(persistenceConfig, dialect);
        context.registerBean(createdDataSource);
        return createdDataSource;
    }

    private TypeConverterRegistry resolveOrRegisterConverterRegistry(Context context, String basePackage) {
        TypeConverterRegistry converterRegistry = context.getBean(TypeConverterRegistry.class);
        if (converterRegistry != null) {
            return converterRegistry;
        }

        TypeConverterRegistry createdRegistry = new TypeConverterRegistry();
        BuiltInConverters.registerAll(createdRegistry);
        ConverterScanner.scanAndRegister(basePackage, createdRegistry);
        context.registerBean(createdRegistry);
        return createdRegistry;
    }

    private EntityMetadataRegistry resolveOrRegisterMetadataRegistry(Context context, TypeConverterRegistry converterRegistry) {
        EntityMetadataRegistry metadataRegistry = context.getBean(EntityMetadataRegistry.class);
        if (metadataRegistry != null) {
            return metadataRegistry;
        }

        EntityMetadataRegistry createdRegistry = new EntityMetadataRegistry(new EntityMetadataParser(converterRegistry));
        context.registerBean(createdRegistry);
        return createdRegistry;
    }

    private ConnectionProvider resolveOrRegisterConnectionProvider(Context context, DataSource dataSource) {
        ConnectionProvider connectionProvider = context.getBean(ConnectionProvider.class);
        if (connectionProvider != null) {
            return connectionProvider;
        }

        ConnectionProvider createdProvider = new ConnectionProvider(dataSource);
        context.registerBean(createdProvider);
        return createdProvider;
    }

    private TransactionManager resolveOrRegisterTransactionManager(Context context, DataSource dataSource) {
        TransactionManager transactionManager = context.getBean(TransactionManager.class);
        if (transactionManager != null) {
            return transactionManager;
        }

        TransactionManager createdManager = new JdbcTransactionManager(dataSource);
        context.registerBean(createdManager);
        return createdManager;
    }

    private JdbcSchemaOptions resolveOrRegisterSchemaOptions(Context context) {
        JdbcSchemaOptions schemaOptions = context.getBean(JdbcSchemaOptions.class);
        if (schemaOptions != null) {
            return schemaOptions;
        }

        JdbcSchemaOptions defaultOptions = new JdbcSchemaOptions();
        context.registerBean(defaultOptions);
        return defaultOptions;
    }

    private void registerDataSourceShutdownHook(Context context, DataSource dataSource) {
        if (!(dataSource instanceof AutoCloseable)) {
            return;
        }

        context.registerShutdownHook(() -> {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "Failed to close DataSource during shutdown", exception);
            }
        });
    }

    private PersistenceConfig discoverPersistenceConfig(Context context, String basePackage) {
        Set<Class<? extends PersistenceConfig>> configs = ReflectionUtils.getSubClassesOf(basePackage, PersistenceConfig.class);

        if (configs.isEmpty()) {
            throw new IllegalStateException(
                    "data-jdbc is on classpath but no PersistenceConfig implementation found in package " + basePackage + ". " +
                            "Ensure that a class implementing PersistenceConfig exists in your plugin."
            );
        }

        if (configs.size() > 1) {
            throw new IllegalStateException(
                    "Multiple PersistenceConfig implementations found: " +
                            configs.stream().map(Class::getName).reduce((a, b) -> a + ", " + b).orElse("")
            );
        }

        Class<? extends PersistenceConfig> configClass = configs.iterator().next();
        context.registerBean(configClass);
        return context.getBean(configClass);
    }

    private void runAutoDdl(String basePackage, EntityMetadataRegistry metadataRegistry, Dialect dialect, ConnectionProvider connectionProvider) {
        Set<Class<?>> entityClasses = ReflectionUtils.getClassesAnnotatedWith(basePackage, Table.class);
        if (entityClasses.isEmpty()) {
            return;
        }

        DdlGenerator ddlGenerator = new DdlGenerator(dialect);

        try (Connection connection = connectionProvider.getConnection();
             Statement stmt = connection.createStatement()) {
            for (Class<?> entityClass : entityClasses) {
                try {
                    EntityMetadata metadata = metadataRegistry.getOrParse(entityClass);
                    String ddl = ddlGenerator.generateCreateTable(metadata);
                    stmt.execute(ddl);
                    LOGGER.log(Level.INFO, "Auto-DDL: created table {0}", metadata.getTableName());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Auto-DDL failed for entity " + entityClass.getName(), e);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to execute auto-DDL", e);
        }
    }
}
