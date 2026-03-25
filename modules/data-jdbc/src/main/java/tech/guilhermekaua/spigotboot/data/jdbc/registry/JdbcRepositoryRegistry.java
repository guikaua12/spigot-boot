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
package tech.guilhermekaua.spigotboot.data.jdbc.registry;

import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.pagination.Page;
import tech.guilhermekaua.spigotboot.core.pagination.Pageable;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Query;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.registry.discovery.JdbcRepositoryDiscoveryService;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.JdbcRepository;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.impl.JdbcRepositoryImpl;
import tech.guilhermekaua.spigotboot.data.jdbc.utils.JdbcTypeUtils;
import tech.guilhermekaua.spigotboot.data.repository.Repository;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@SuppressWarnings("rawtypes")
@RequiredArgsConstructor
public class JdbcRepositoryRegistry {
    private static final Logger LOGGER = Logger.getLogger(JdbcRepositoryRegistry.class.getName());

    private final Map<Class<?>, JdbcRepositoryImpl<?, ?>> repositoryMap = new HashMap<>();
    private final JdbcRepositoryDiscoveryService discoveryService;

    public void initialize(Context context, ConnectionProvider connectionProvider, Dialect dialect, EntityMetadataRegistry metadataRegistry) {
        Set<Class<? extends JdbcRepository>> repositoryClasses = discoveryService.discoverFromPackage(
                context.getPlugin().getMainClass().getPackage().getName()
        );

        DependencyManager dependencyManager = context.getDependencyManager();

        for (Class<? extends JdbcRepository> repositoryClass : repositoryClasses) {
            try {
                initializeRepository(repositoryClass, dependencyManager, connectionProvider, dialect, metadataRegistry);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to initialize repository " + repositoryClass.getName(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initializeRepository(
            Class<? extends JdbcRepository> repositoryClass,
            DependencyManager dependencyManager,
            ConnectionProvider connectionProvider,
            Dialect dialect,
            EntityMetadataRegistry metadataRegistry
    ) {
        validateRepositoryContract(repositoryClass);

        Class<?> entityClass = JdbcTypeUtils.resolveEntityType(repositoryClass);
        if (entityClass == null) {
            LOGGER.log(Level.WARNING, "Skipping repository {0}: could not resolve entity type.", repositoryClass.getName());
            return;
        }

        // create the implementation
        EntityMetadata metadata = metadataRegistry.getOrParse(entityClass);
        JdbcRepositoryImpl<?, ?> impl = new JdbcRepositoryImpl<>(connectionProvider, metadata, dialect, metadataRegistry);
        repositoryMap.put(entityClass, impl);

        // register the repository interface as a bean (with proxy) only after impl is ready
        dependencyManager.registerDependency(
                (Class<JdbcRepository>) repositoryClass,
                repositoryClass,
                BeanUtils.getQualifier(repositoryClass),
                BeanUtils.getIsPrimary(repositoryClass),
                (clazz) -> ComponentProxy.createProxy(clazz, null, new Class[0], new Object[0])
        );
    }

    private void validateRepositoryContract(Class<? extends JdbcRepository> repositoryClass) {
        for (Method method : repositoryClass.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isDefault()) {
                continue;
            }

            if (isBaseRepositoryMethod(method)) {
                continue;
            }

            if (method.isAnnotationPresent(Query.class)) {
                validatePagedQueryMethodSignature(repositoryClass, method);
                continue;
            }

            throw new IllegalStateException(
                    "Invalid repository method " + repositoryClass.getName() + "#" + method.getName() +
                            ". Custom methods must be default methods or annotated with @Query."
            );
        }
    }

    private boolean isBaseRepositoryMethod(Method method) {
        return isDeclaredBy(method, JdbcRepository.class) || isDeclaredBy(method, Repository.class);
    }

    private void validatePagedQueryMethodSignature(Class<? extends JdbcRepository> repositoryClass, Method method) {
        if (!Page.class.isAssignableFrom(method.getReturnType())) {
            return;
        }

        int pageableParameterCount = 0;
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (Pageable.class.isAssignableFrom(parameterType)) {
                pageableParameterCount++;
            }
        }

        if (pageableParameterCount != 1) {
            throw new IllegalStateException(
                    "Invalid paged @Query method " + repositoryClass.getName() + "#" + method.getName() +
                            ". Methods returning Page must declare exactly one Pageable parameter."
            );
        }
    }

    private boolean isDeclaredBy(Method method, Class<?> baseType) {
        try {
            baseType.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public <T, ID> JdbcRepositoryImpl<T, ID> getRepository(Class<T> entityClass) {
        return (JdbcRepositoryImpl<T, ID>) repositoryMap.get(entityClass);
    }
}
