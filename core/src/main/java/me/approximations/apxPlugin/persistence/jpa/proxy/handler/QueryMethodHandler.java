package me.approximations.apxPlugin.persistence.jpa.proxy.handler;

import com.google.common.collect.ImmutableSet;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.persistence.jpa.context.PersistenceContext;
import me.approximations.apxPlugin.utils.EntityManagerTransactionUtils;
import org.hibernate.Session;

import javax.persistence.Query;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

@RequiredArgsConstructor
public class QueryMethodHandler implements MethodHandler {
    private static final Set<String> QUERY_TERMINATING_METHODS = ImmutableSet.of(
            "execute",  // jakarta.persistence.StoredProcedureQuery.execute()
            "executeUpdate", // jakarta.persistence.Query.executeUpdate()
            "getSingleResult",  // jakarta.persistence.Query.getSingleResult()
            "getResultStream",  // jakarta.persistence.Query.getResultStream()
            "getResultList",  // jakarta.persistence.Query.getResultList()
            "list",  // org.hibernate.query.Query.list()
            "scroll",  // org.hibernate.query.Query.scroll()
            "stream",  // org.hibernate.query.Query.stream()
            "uniqueResult",  // org.hibernate.query.Query.uniqueResult()
            "uniqueResultOptional"  // org.hibernate.query.Query.uniqueResultOptional()
    );

    private final Session entityManager;
    private final Query query;

    public static Query createProxy(Session entityManager, Query query) {
        final QueryMethodHandler queryMethodHandler = new QueryMethodHandler(entityManager, query);

        final ProxyFactory queryProxyFactory = new ProxyFactory();
        queryProxyFactory.setInterfaces(query.getClass().getInterfaces());

        try {
            return (Query) queryProxyFactory.create(new Class[0], new Object[0], queryMethodHandler);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (PersistenceContext.hasSession()) {
            throw new IllegalStateException("Session found in PersistenceContext");
        }

        if (!QUERY_TERMINATING_METHODS.contains(thisMethod.getName())) {
            final Object result = thisMethod.invoke(query, args);
            if (result instanceof Query) {
                return createProxy(entityManager, (Query) result);
            }
        }

        return EntityManagerTransactionUtils.executeTransaction(entityManager, () -> thisMethod.invoke(query, args));
    }
}
