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
package tech.guilhermekaua.spigotboot.data.ormLite.registry;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.data.ormLite.annotations.OrmLiteDao;
import tech.guilhermekaua.spigotboot.data.ormLite.registry.discovery.OrmLiteRepositoryDiscoveryService;
import tech.guilhermekaua.spigotboot.data.ormLite.repository.OrmLiteRepository;
import tech.guilhermekaua.spigotboot.data.ormLite.repository.impl.OrmLiteRepositoryImpl;
import tech.guilhermekaua.spigotboot.data.ormLite.utils.OrmLiteTypeUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@SuppressWarnings("rawtypes")
@RequiredArgsConstructor
public class OrmLiteRepositoryRegistry {
    private static final Logger LOGGER = Logger.getLogger(OrmLiteRepositoryRegistry.class.getName());

    private final Map<Class<?>, OrmLiteRepository<?, ?>> repositoryMap = new HashMap<>();
    private final OrmLiteRepositoryDiscoveryService repositoryDiscoveryService;

    public void initialize(Context context) {
        Set<Class<? extends OrmLiteRepository>> repositoryClasses = repositoryDiscoveryService.discoverFromPackage(
                context.getPlugin().getMainClass().getPackage().getName()
        );

        ConnectionSource connectionSource = context.getBean(ConnectionSource.class);

        if (connectionSource == null) {
            throw new IllegalStateException("ConnectionSource is not available. Ensure that the DataOrmLiteModule has been initialized.");
        }

        DependencyManager dependencyManager = context.getDependencyManager();

        for (Class<? extends OrmLiteRepository> repositoryClass : repositoryClasses) {
            try {
                initializeRepository(repositoryClass, dependencyManager, connectionSource);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initializeRepository(Class<? extends OrmLiteRepository> repositoryClass, DependencyManager dependencyManager, ConnectionSource connectionSource) {
        try {
            Class<?> entityClass = OrmLiteTypeUtils.resolveEntityType(repositoryClass);
            if (entityClass == null) {
                LOGGER.log(Level.WARNING, "Skipping repository {0}: could not resolve entity type (unbound type variables).", repositoryClass.getName());
                return;
            }

            dependencyManager.registerDependency(
                    (Class<OrmLiteRepository>) repositoryClass,
                    repositoryClass,
                    BeanUtils.getQualifier(repositoryClass),
                    BeanUtils.getIsPrimary(repositoryClass),
                    (clazz) -> ComponentProxy.createProxy(clazz, null, new Class[0], new Object[0])
            );

            Dao<?, ?> dao = DaoManager.createDao(connectionSource, entityClass);

            OrmLiteRepository repository = dependencyManager.resolveDependency(repositoryClass, BeanUtils.getQualifier(repositoryClass));
            OrmLiteRepositoryImpl defaultImpl = new OrmLiteRepositoryImpl<>(dao);

            repositoryMap.put(entityClass, defaultImpl);

            setDaoObject(repository, dao);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to register repository " + repositoryClass.getName(), t);
        }
    }

    private void setDaoObject(OrmLiteRepository repository, Dao<?, ?> dao) {
        try {
            for (Field field : ReflectionUtils.getFieldsAnnotatedWith(OrmLiteDao.class, repository.getClass())) {
                if (!Dao.class.isAssignableFrom(field.getType())) {
                    throw new IllegalStateException("Field annotated with @OrmLiteDao must be of type " + Dao.class.getName() + ": " + field.getName());
                }
                field.setAccessible(true);
                field.set(repository, dao);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set DAO for repository: " + repository.getClass().getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T, ID> OrmLiteRepository<T, ID> getDao(Class<T> entityClass) {
        return (OrmLiteRepository<T, ID>) repositoryMap.get(entityClass);
    }
}
