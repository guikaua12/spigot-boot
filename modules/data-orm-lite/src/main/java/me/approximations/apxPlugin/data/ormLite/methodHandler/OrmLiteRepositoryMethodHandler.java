package me.approximations.apxPlugin.data.ormLite.methodHandler;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.MethodHandler;
import me.approximations.apxPlugin.data.ormLite.registry.OrmLiteRepositoryRegistry;
import me.approximations.apxPlugin.data.ormLite.repository.OrmLiteRepository;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

@RequiredArgsConstructor
public class OrmLiteRepositoryMethodHandler implements MethodHandler<OrmLiteRepository<?, ?>> {
    private final OrmLiteRepositoryRegistry repositoryRegistry;

    @Override
    public boolean canHandle(OrmLiteRepository<?, ?> self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (self == null || thisMethod == null) {
            return false;
        }

        return Arrays.stream(self.getClass().getInterfaces())
                .anyMatch(OrmLiteRepository.class::isAssignableFrom);
    }

    @Override
    public Object handle(OrmLiteRepository<?, ?> selfProxy, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        try {
            return proceed.invoke(selfProxy, args);
        } catch (IllegalAccessException | IllegalArgumentException | NullPointerException e) {
            Class<?> self = selfProxy.getClass().getInterfaces()[0];
            Type[] types = ((ParameterizedType) self.getGenericInterfaces()[0]).getActualTypeArguments();
            Class<?> entityType = (Class<?>) types[0];

            OrmLiteRepository<?, ?> repositoryImpl = repositoryRegistry.getDao(entityType);

            if (repositoryImpl == null) {
                throw new IllegalStateException("No repository found for entity type: " + entityType.getName());
            }

            Method method = repositoryImpl.getClass().getMethod(thisMethod.getName(), thisMethod.getParameterTypes());
            method.setAccessible(true);
            return method.invoke(repositoryImpl, args);
        }
    }
}
