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
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Table;
import tech.guilhermekaua.spigotboot.data.jdbc.config.JdbcSchemaOptions;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.ddl.DdlGenerator;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.registry.JdbcRepositoryRegistry;

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

        Dialect dialect = resolveRequiredBean(context, Dialect.class);
        DataSource dataSource = resolveRequiredBean(context, DataSource.class);
        registerDataSourceShutdownHook(context, dataSource);
        EntityMetadataRegistry metadataRegistry = resolveRequiredBean(context, EntityMetadataRegistry.class);
        ConnectionProvider connectionProvider = resolveRequiredBean(context, ConnectionProvider.class);
        JdbcSchemaOptions schemaOptions = resolveRequiredBean(context, JdbcSchemaOptions.class);

        if (schemaOptions.isAutoDdlEnabled()) {
            runAutoDdl(basePackage, metadataRegistry, dialect, connectionProvider);
        }

        repositoryRegistry.initialize(context, connectionProvider, dialect, metadataRegistry);
    }

    private <T> T resolveRequiredBean(Context context, Class<T> beanType) {
        T bean = context.getBean(beanType);
        if (bean != null) {
            return bean;
        }

        throw new IllegalStateException(
                "Failed to resolve required bean for data-jdbc module initialization: " + beanType.getName()
        );
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
