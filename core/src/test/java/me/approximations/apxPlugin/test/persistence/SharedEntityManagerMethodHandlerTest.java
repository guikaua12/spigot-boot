package me.approximations.apxPlugin.test.persistence;

import javassist.util.proxy.ProxyFactory;
import me.approximations.apxPlugin.persistence.jpa.proxy.handler.SharedEntityManagerMethodHandler;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;

public class SharedEntityManagerMethodHandlerTest {
    @Test
    public void test() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setInterfaces(new Class<?>[]{EntityManager.class});

        final EntityManager entityManager = (EntityManager) proxyFactory.create(new Class<?>[0], new Object[0], new SharedEntityManagerMethodHandler(null));
        System.out.println(entityManager.isOpen());
    }
}
