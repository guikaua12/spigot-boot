package me.approximations.apxPlugin.persistence.jpa.service.register;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import me.approximations.apxPlugin.persistence.jpa.service.annotations.Service;
import me.approximations.apxPlugin.persistence.jpa.service.config.impl.DefaultServiceConfig;
import me.approximations.apxPlugin.persistence.jpa.service.proxy.ServiceProxy;
import me.approximations.apxPlugin.utils.ReflectionUtils;

import javax.persistence.EntityManagerFactory;
import java.util.Set;

@RequiredArgsConstructor
public class ServicesRegister {
    private final EntityManagerFactory entityManagerFactory;
    private final DependencyManager dependencyManager;

    public void register() {
        final Set<Class<?>> servicesClasses = discoverServices();
        for (final Class<?> servicesClass : servicesClasses) {
            final Object serviceProxy = ServiceProxy.createProxy(servicesClass, entityManagerFactory, DefaultServiceConfig.INSTANCE);
            dependencyManager.registerDependency(servicesClass, serviceProxy);
        }

    }

    private Set<Class<?>> discoverServices() {
        return ReflectionUtils.getClassesAnnotatedWith(Service.class);
    }
}
