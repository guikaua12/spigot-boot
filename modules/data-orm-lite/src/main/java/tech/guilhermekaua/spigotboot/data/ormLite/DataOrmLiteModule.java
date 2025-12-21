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
package tech.guilhermekaua.spigotboot.data.ormLite;

import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.data.config.impl.HikariPersistenceUnitConfig;
import tech.guilhermekaua.spigotboot.data.ormLite.config.PersistenceConfig;
import tech.guilhermekaua.spigotboot.data.ormLite.config.registry.PersistenceConfigRegistry;
import tech.guilhermekaua.spigotboot.data.ormLite.registry.OrmLiteRepositoryRegistry;

import javax.sql.DataSource;
import java.sql.SQLException;

public class DataOrmLiteModule implements Module {
    @Inject
    private OrmLiteRepositoryRegistry ormLiteRepositoryRegistry;
    @Inject
    private PersistenceConfigRegistry persistenceConfigRegistry;

    @Override
    public void onInitialize(Context context) throws Exception {
        registerConnectionSource(context);

        ormLiteRepositoryRegistry.initialize(context);
    }

    private void registerConnectionSource(Context context) throws SQLException {
        PersistenceConfig persistenceConfig = persistenceConfigRegistry.initialize(context);
        ConnectionSource connectionSource = createConnectionSource(persistenceConfig);

        context.registerBean(connectionSource);
    }

    public ConnectionSource createConnectionSource(PersistenceConfig persistenceConfig) throws SQLException {
        final DataSource dataSource = new HikariPersistenceUnitConfig().configure(
                persistenceConfig.getAddress(),
                persistenceConfig.getUsername(),
                persistenceConfig.getPassword()
        );

        return new DataSourceConnectionSource(dataSource, persistenceConfig.getAddress());
    }
}
