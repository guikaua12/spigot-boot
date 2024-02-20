package me.approximations.apxPlugin.persistence.jpa.proxy.handler;

import com.google.common.collect.ImmutableSet;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.persistence.jpa.context.PersistenceContext;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

@RequiredArgsConstructor
public class SharedEntityManagerMethodHandler implements MethodHandler {
    private static final Set<String> TRANSACTION_REQUIRING_METHODS = ImmutableSet.of(
            "joinTransaction",
            "flush",
            "persist",
            "merge",
            "remove",
            "refresh");

    private final EntityManagerFactory entityManagerFactory;

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        switch (thisMethod.getName()) {
            case "equals": {
                return (self == args[0]);
            }
            case "hashCode": {
                return hashCode();
            }
            case "toString": {
                return "SharedEntityManager proxy";
            }
            case "getEntityManagerFactory": {
                return this.entityManagerFactory;
            }
            case "getMetamodel":
            case "getCriteriaBuilder": {
                try {
                    return EntityManagerFactory.class.getMethod(thisMethod.getName()).invoke(this.entityManagerFactory);
                } catch (InvocationTargetException ex) {
                    throw ex.getTargetException();
                }
            }
            case "unwrap": {
                Class<?> targetClass = (Class<?>) args[0];
                if (targetClass != null && targetClass.isInstance(self)) {
                    return self;
                }
            }
            case "isOpen": {
                return true;
            }
            case "close": {
                return null;
            }
            case "getTransaction": {
                throw new IllegalStateException("Not allowed to create a transaction using a EntityManager proxy.");
            }
        }

        final EntityManager entityManager = getEntityManager();

        if (TRANSACTION_REQUIRING_METHODS.contains(thisMethod.getName())) {
            if (PersistenceContext.hasEntityManager()) {
                return proceed.invoke(self, args);
            }

            final EntityTransaction transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                final Object result = proceed.invoke(self, args);
                transaction.commit();

                return result;
            } catch (Throwable throwable) {
                transaction.rollback();
                throw new RuntimeException(throwable);
            } finally {
                entityManager.close();
            }
        }

        final Object result = proceed.invoke(self, args);
        if (PersistenceContext.hasEntityManager() || !(result instanceof Query)) {
            return result;
        }


        final Query query = (Query) result;
        final QueryMethodHandler queryMethodHandler = new QueryMethodHandler(entityManager, query);

        final ProxyFactory queryProxyFactory = new ProxyFactory();
        queryProxyFactory.setInterfaces(query.getClass().getInterfaces());

        return queryProxyFactory.create(new Class[0], new Object[0], queryMethodHandler);
    }

    private EntityManager getEntityManager() {
        final EntityManager contextEntityManager = PersistenceContext.getEntityManager();

        return contextEntityManager != null ? contextEntityManager : entityManagerFactory.createEntityManager();
    }
}
