package me.approximations.apxPlugin;

import com.google.common.base.Stopwatch;
import lombok.Getter;
import me.approximations.apxPlugin.dependencyInjection.manager.DependencyManager;
import me.approximations.apxPlugin.listener.manager.ListenerManager;
import me.approximations.apxPlugin.persistence.jpa.config.PersistenceConfig;
import me.approximations.apxPlugin.persistence.jpa.config.PersistenceUnitConfig;
import me.approximations.apxPlugin.persistence.jpa.config.discovery.PersistenceConfigDiscovery;
import me.approximations.apxPlugin.persistence.jpa.config.impl.HikariPersistenceUnitConfig;
import me.approximations.apxPlugin.persistence.jpa.repository.impl.SimpleJpaRepository;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

import javax.persistence.Entity;
import javax.persistence.EntityManagerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ApxPlugin extends JavaPlugin {
    private final List<Runnable> disableEntries = new ArrayList<>();

    @Getter
    private Reflections reflections;
    @Getter
    private DependencyManager dependencyManager;
    @Getter
    private ListenerManager listenerManager;
    private EntityManagerFactory entityManagerFactory;

    @SuppressWarnings("rawtypes")
    @Override
    public void onEnable() {
        final Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            Logger.getLogger("org.hibernate").setLevel(Level.OFF);

            this.reflections = new Reflections(getClass().getPackage().getName(), new SubTypesScanner(), new TypeAnnotationsScanner());
            this.dependencyManager = new DependencyManager(reflections);
            dependencyManager.registerDependencies();

            dependencyManager.registerDependency(Plugin.class, this);
            dependencyManager.registerDependency(JavaPlugin.class, this);
            dependencyManager.registerDependency(getClass(), this);

            final Optional<PersistenceConfig> persistenceConfigOptional = new PersistenceConfigDiscovery(reflections).discovery();
            persistenceConfigOptional.ifPresent(config -> {
                dependencyManager.injectDependencies(config);

                final List<Class<?>> jpaEntities = getJpaEntities();
                final PersistenceUnitConfig persistenceUnitConfig = new HikariPersistenceUnitConfig(config, jpaEntities);

                final HibernatePersistenceProvider provider = new HibernatePersistenceProvider();

                entityManagerFactory = provider.createContainerEntityManagerFactory(
                        persistenceUnitConfig,
                        persistenceUnitConfig.getProperties()
                );

                final List<Class<? extends SimpleJpaRepository>> repositories = getRepositories();

                for (final Class<? extends SimpleJpaRepository> repository : repositories) {
                    final Type[] types = ((ParameterizedType) repository.getGenericSuperclass()).getActualTypeArguments();

                    try {
                        final Class<?> entityClass = Class.forName(types[0].getTypeName());

                        final SimpleJpaRepository<?, ?> simpleJpaRepository = repository.getConstructor(EntityManagerFactory.class, Class.class).newInstance(entityManagerFactory, entityClass);
                        dependencyManager.registerDependency(simpleJpaRepository.getClass(), simpleJpaRepository);
                    } catch (ClassNotFoundException | InvocationTargetException | InstantiationException |
                             NoSuchMethodException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            });


            this.listenerManager = new ListenerManager(this, reflections, dependencyManager);

            dependencyManager.injectDependencies();
            onPluginEnable();

            getLogger().info(String.format("Plugin enabled successfully! (%s)", stopwatch.stop()));
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "An error happened while enabling.");

            t.printStackTrace();
        } finally {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
            }
        }
    }

    private List<Class<?>> getJpaEntities() {
        final Set<Class<?>> entities = reflections.getTypesAnnotatedWith(Entity.class);
        return new ArrayList<>(entities);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Class<? extends SimpleJpaRepository>> getRepositories() {
        final Set<Class<? extends SimpleJpaRepository>> repositories = reflections.getSubTypesOf(SimpleJpaRepository.class);

        return new ArrayList<>(repositories);
    }

    protected void onPluginEnable() {
    }

    @Override
    public void onDisable() {
        for (Runnable runnable : disableEntries) {
            runnable.run();
        }
    }

    public void addDisableEntry(Runnable runnable) {
        disableEntries.add(runnable);
    }
}