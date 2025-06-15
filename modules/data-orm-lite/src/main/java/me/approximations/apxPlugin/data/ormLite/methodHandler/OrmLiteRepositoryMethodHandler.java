package me.approximations.apxPlugin.data.ormLite.methodHandler;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.annotations.RegisterMethodHandler;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import me.approximations.apxPlugin.data.ormLite.registry.OrmLiteRepositoryRegistry;
import me.approximations.apxPlugin.data.ormLite.repository.OrmLiteRepository;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@RequiredArgsConstructor
@RegisterMethodHandler
public class OrmLiteRepositoryMethodHandler {
    private final OrmLiteRepositoryRegistry repositoryRegistry;

    @MethodHandler(targetClass = OrmLiteRepository.class)
    public Object handle(MethodHandlerContext context) throws Throwable {
        if (context.self() == null || context.thisMethod() == null) {
            return null;
        }

        try {
            return context.proceed().invoke(context.self(), context.args());
        } catch (IllegalAccessException | IllegalArgumentException | NullPointerException e) {
            Class<?> self = context.self().getClass().getInterfaces()[0];
            Type[] types = ((ParameterizedType) self.getGenericInterfaces()[0]).getActualTypeArguments();
            Class<?> entityType = (Class<?>) types[0];

            OrmLiteRepository<?, ?> repositoryImpl = repositoryRegistry.getDao(entityType);

            if (repositoryImpl == null) {
                throw new IllegalStateException("No repository found for entity type: " + entityType.getName());
            }

            Method method = repositoryImpl.getClass().getMethod(context.thisMethod().getName(), context.thisMethod().getParameterTypes());
            method.setAccessible(true);
            return method.invoke(repositoryImpl, context.args());
        }
    }
}
