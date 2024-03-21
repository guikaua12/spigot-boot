package me.approximations.apxPlugin.persistence.jpa.service.proxy;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.persistence.jpa.context.PersistenceContext;
import me.approximations.apxPlugin.persistence.jpa.service.annotations.Service;
import me.approximations.apxPlugin.persistence.jpa.service.config.ServiceConfig;
import me.approximations.apxPlugin.utils.MethodFormatUtils;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.persistence.EntityManagerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class ServiceProxy implements MethodHandler {
    private final EntityManagerFactory entityManagerFactory;
    private final ServiceConfig serviceConfig;

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> serviceClass, EntityManagerFactory entityManagerFactory, ServiceConfig serviceConfig) {
        final ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setSuperclass(serviceClass);

        final T service;

        try {
            service = serviceClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        final ServiceProxy serviceProxy = new ServiceProxy(entityManagerFactory, serviceConfig);

        try {
            return (T) proxyFactory.create(new Class<?>[0], new Object[0], serviceProxy);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (!thisMethod.isAnnotationPresent(Service.class)) return proceed.invoke(self, args);
        if (thisMethod.getReturnType() != CompletableFuture.class) {
            throw new IllegalStateException(String.format("Service method %s must return CompletableFuture", MethodFormatUtils.formatMethod(thisMethod)));
        }

        if (PersistenceContext.hasSession()) {
            return proceed.invoke(self, args);
        }

        return CompletableFuture.supplyAsync(() -> {
            final Session session = (Session) entityManagerFactory.createEntityManager();
            PersistenceContext.setSession(session);
            final Transaction transaction = session.getTransaction();

            try {
                transaction.begin();
                final CompletableFuture<?> result = (CompletableFuture<?>) proceed.invoke(self, args);
                transaction.commit();

                return result.join();
            } catch (Throwable throwable) {
                transaction.rollback();
                if (!(throwable.getCause().getCause() instanceof RuntimeException)) {
                    throw new IllegalStateException("Service method " + MethodFormatUtils.formatMethod(thisMethod) + " threw a checked exception", throwable.getCause().getCause());
                }
                throw (RuntimeException) throwable.getCause().getCause();
            } finally {
                PersistenceContext.removeSession();
                session.close();
            }
        }, serviceConfig.getExecutorService());
    }
}
