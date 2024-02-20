package me.approximations.apxPlugin.persistence.jpa.proxy.handler;

import com.google.common.collect.ImmutableSet;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.persistence.jpa.context.PersistenceContext;
import me.approximations.apxPlugin.utils.EntityManagerTransactionUtils;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
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

    public static EntityManager createProxy(EntityManagerFactory entityManagerFactory) {
        final ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setInterfaces(new Class<?>[]{EntityManager.class});

        final SharedEntityManagerMethodHandler sharedEntityManagerMethodHandler = new SharedEntityManagerMethodHandler(entityManagerFactory);
        try {
            return (EntityManager) proxyFactory.create(new Class<?>[0], new Object[0], sharedEntityManagerMethodHandler);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

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
                return thisMethod.invoke(entityManager, args);
            }

            return EntityManagerTransactionUtils.executeTransaction(entityManager, () -> thisMethod.invoke(entityManager, args));
        }

        final Object result = thisMethod.invoke(entityManager, args);
        if (PersistenceContext.hasEntityManager() || !(result instanceof Query)) {
            return result;
        }


        return QueryMethodHandler.createProxy(entityManager, (Query) result);
    }

    private EntityManager getEntityManager() {
        final EntityManager contextEntityManager = PersistenceContext.getEntityManager();

        return contextEntityManager != null ? contextEntityManager : entityManagerFactory.createEntityManager();
    }
}
