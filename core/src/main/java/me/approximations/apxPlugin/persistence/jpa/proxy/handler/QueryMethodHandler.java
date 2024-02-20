package me.approximations.apxPlugin.persistence.jpa.proxy.handler;

import javassist.util.proxy.MethodHandler;
import lombok.RequiredArgsConstructor;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.lang.reflect.Method;

@RequiredArgsConstructor
public class QueryMethodHandler implements MethodHandler {
    private final EntityManager entityManager;
    private final Query query;


    @Override
    public Object invoke(Object o, Method method, Method method1, Object[] objects) throws Throwable {
        return null;
    }
}
