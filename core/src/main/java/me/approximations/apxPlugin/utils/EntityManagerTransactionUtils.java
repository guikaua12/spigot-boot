package me.approximations.apxPlugin.utils;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.concurrent.Callable;

public class EntityManagerTransactionUtils {
    public static Object executeTransaction(EntityManager entityManager, Callable<Object> callable) {
        final EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();
            final Object result = callable.call();
            transaction.commit();

            return result;
        } catch (Throwable throwable) {
            transaction.rollback();
            throw new RuntimeException(throwable);
        } finally {
            entityManager.close();
        }
    }
}
