package me.approximations.apxPlugin.persistence.jpa.config.discovery;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.persistence.jpa.config.PersistenceConfig;
import me.approximations.apxPlugin.utils.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
public class PersistenceConfigDiscovery {

    public Optional<PersistenceConfig> discovery() {
        // TODO: allow multiple PersistenceConfig implementations
        final Set<Class<? extends PersistenceConfig>> configs = ReflectionUtils.getSubClassesOf(PersistenceConfig.class);

        if (configs.isEmpty()) return Optional.empty();

        final List<Class<? extends PersistenceConfig>> configsList = new ArrayList<>(configs);

        try {
            final Constructor<? extends PersistenceConfig> declaredConstructor = configsList.get(0).getDeclaredConstructor();

            if (declaredConstructor == null) {
                throw new IllegalStateException("No default constructor found for " + configsList.get(0).getName());
            }

            return Optional.of(declaredConstructor.newInstance());
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
