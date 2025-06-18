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
package me.approximations.spigotboot.data.ormLite.registry;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.di.annotations.Component;
import me.approximations.spigotboot.core.di.manager.DependencyManager;
import me.approximations.spigotboot.core.reflection.DiscoveryService;
import me.approximations.spigotboot.core.utils.ReflectionUtils;
import me.approximations.spigotboot.data.ormLite.annotations.OrmLiteDao;
import me.approximations.spigotboot.data.ormLite.registry.discovery.OrmLiteRepositoryDiscoveryService;
import me.approximations.spigotboot.data.ormLite.repository.OrmLiteRepository;
import me.approximations.spigotboot.data.ormLite.repository.impl.OrmLiteRepositoryImpl;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
@SuppressWarnings("rawtypes")
@RequiredArgsConstructor
public class OrmLiteRepositoryRegistry {
    private final Map<Class<?>, OrmLiteRepository<?, ?>> repositoryMap = new HashMap<>();
    private final DependencyManager dependencyManager;
    private final ConnectionSource connectionSource;
    private final Plugin plugin;

    public void initialize() {
        final DiscoveryService<Class<? extends OrmLiteRepository>> discoveryService = new OrmLiteRepositoryDiscoveryService(plugin);
        Collection<Class<? extends OrmLiteRepository>> repositoryClasses = discoveryService.discoverAll();

        for (Class<? extends OrmLiteRepository> repositoryClass : repositoryClasses) {
            try {
                dependencyManager.registerDependency(repositoryClass);

                Type[] types = ((ParameterizedType) repositoryClass.getGenericInterfaces()[0]).getActualTypeArguments();
                Type entityType = types[0];

                Class<?> entityClass = Class.forName(entityType.getTypeName());
                Dao<?, ?> dao = DaoManager.createDao(connectionSource, entityClass);

                OrmLiteRepository repository = dependencyManager.resolveDependency(repositoryClass);
                OrmLiteRepositoryImpl defaultImpl = new OrmLiteRepositoryImpl<>(dao);

                repositoryMap.put(entityClass, defaultImpl);

                setDaoObject(repository, dao);
            } catch (Exception e) {
                throw new RuntimeException("Failed to register repository: " + repositoryClass.getName(), e);
            }
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
