package me.approximations.apxPlugin.test.persistence;

import me.approximations.apxPlugin.persistence.jpa.proxy.handler.SharedSessionMethodHandler;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

public class SharedSessionMethodHandlerTest {
    @Test
    public void test() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final Session entityManager = SharedSessionMethodHandler.createProxy(null);
        System.out.println(entityManager.isOpen());
    }
}
