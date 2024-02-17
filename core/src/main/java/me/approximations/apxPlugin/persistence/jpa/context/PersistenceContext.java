package me.approximations.apxPlugin.persistence.jpa.context;

import javax.persistence.EntityManager;

public class PersistenceContext {
    private static final ThreadLocal<EntityManager> THREAD_LOCAL_ENTITY_MANAGER = new ThreadLocal<>();

    public static EntityManager getEntityManager() {
        return THREAD_LOCAL_ENTITY_MANAGER.get();
    }

    public static void setEntityManager(EntityManager entityManager) {
        THREAD_LOCAL_ENTITY_MANAGER.set(entityManager);
    }

    public static void removeEntityManager() {
        THREAD_LOCAL_ENTITY_MANAGER.remove();
    }

    public static boolean hasEntityManager() {
        return THREAD_LOCAL_ENTITY_MANAGER.get() != null;
    }
}
