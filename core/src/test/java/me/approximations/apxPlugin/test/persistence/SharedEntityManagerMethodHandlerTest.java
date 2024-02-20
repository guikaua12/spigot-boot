package me.approximations.apxPlugin.test.persistence;

import me.approximations.apxPlugin.persistence.jpa.proxy.handler.SharedEntityManagerMethodHandler;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;

public class SharedEntityManagerMethodHandlerTest {
    @Test
    public void test() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final EntityManager entityManager = SharedEntityManagerMethodHandler.createProxy(null);
        System.out.println(entityManager.isOpen());
    }
}
