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
package tech.guilhermekaua.spigotboot.data.jdbc.config;

import com.zaxxer.hikari.HikariDataSource;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnMissingBean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.data.config.PersistenceConfig;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.BuiltInConverters;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.ConverterScanner;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.DialectResolver;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataParser;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.transaction.JdbcTransactionManager;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionManager;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class DataJdbcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PersistenceConfig.class)
    public PersistenceConfig persistenceConfig(BootPlugin plugin, DependencyManager dependencyManager) {
        String basePackage = plugin.getMainClass().getPackage().getName();
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
                            configs.stream().map(Class::getName).collect(Collectors.joining(", "))
            );
        }

        Class<? extends PersistenceConfig> configClass = configs.iterator().next();
        return instantiatePersistenceConfig(configClass, dependencyManager);
    }

    @Bean
    @ConditionalOnMissingBean(Dialect.class)
    public Dialect dialect(PersistenceConfig persistenceConfig) {
        return DialectResolver.resolve(persistenceConfig.getAddress());
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(PersistenceConfig config, Dialect dialect) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(config.getAddress());
        ds.setUsername(config.getUsername());
        ds.setPassword(config.getPassword());
        dialect.configureDataSource(ds);
        return ds;
    }

    @Bean
    @ConditionalOnMissingBean(TypeConverterRegistry.class)
    public TypeConverterRegistry typeConverterRegistry(BootPlugin plugin) {
        String basePackage = plugin.getMainClass().getPackage().getName();

        TypeConverterRegistry registry = new TypeConverterRegistry();
        BuiltInConverters.registerAll(registry);
        ConverterScanner.scanAndRegister(basePackage, registry);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(EntityMetadataRegistry.class)
    public EntityMetadataRegistry entityMetadataRegistry(TypeConverterRegistry converterRegistry) {
        return new EntityMetadataRegistry(new EntityMetadataParser(converterRegistry));
    }

    @Bean
    @ConditionalOnMissingBean(ConnectionProvider.class)
    public ConnectionProvider connectionProvider(DataSource dataSource) {
        return new ConnectionProvider(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(TransactionManager.class)
    public TransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(JdbcSchemaOptions.class)
    public JdbcSchemaOptions jdbcSchemaOptions() {
        return new JdbcSchemaOptions();
    }

    private PersistenceConfig instantiatePersistenceConfig(
            Class<? extends PersistenceConfig> configClass,
            DependencyManager dependencyManager
    ) {
        try {
            Constructor<?> constructor = dependencyManager.findInjectConstructor(configClass);
            if (constructor == null) {
                throw new IllegalStateException(
                        "No injectable constructor found for PersistenceConfig implementation: " + configClass.getName()
                );
            }

            Object[] constructorArguments = dependencyManager.resolveArguments(constructor);
            constructor.setAccessible(true);
            return configClass.cast(constructor.newInstance(constructorArguments));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to instantiate PersistenceConfig implementation: " + configClass.getName(),
                    exception
            );
        }
    }
}
