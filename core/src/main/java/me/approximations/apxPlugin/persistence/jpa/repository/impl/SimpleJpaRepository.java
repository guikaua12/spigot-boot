package me.approximations.apxPlugin.persistence.jpa.repository.impl;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.persistence.jpa.context.PersistenceContext;
import me.approximations.apxPlugin.persistence.jpa.repository.JpaRepository;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.List;
import java.util.concurrent.Callable;

@RequiredArgsConstructor
@AllArgsConstructor
public class SimpleJpaRepository<T, KEY> implements JpaRepository<T, KEY> {
    private EntityManagerFactory entityManagerFactory;
    private final Class<T> entityClass;

    @Override
    public T save(T entity) {
        final EntityManager entityManager = getEntityManager();
        runInsideTransactionIfNotActive(entityManager, () -> entityManager.persist(entity));
        return entity;
    }

    @Override
    public T findById(KEY id) {
        return getEntityManager().find(entityClass, id);
    }

    @Override
    public List<T> findAll() {
        return getEntityManager().createQuery(String.format("SELECT e FROM %s e", entityClass.getSimpleName()), entityClass).getResultList();
    }

    @Override
    public void delete(T entity) {
        final EntityManager entityManager = getEntityManager();
        runInsideTransactionIfNotActive(entityManager, () -> entityManager.createQuery(String.format("DELETE FROM %s e WHERE e = :entity", entityClass.getSimpleName()))
                .setParameter("entity", entity)
                .executeUpdate());
    }

    @Override
    public void deleteById(KEY id) {
        final EntityManager entityManager = getEntityManager();
        runInsideTransactionIfNotActive(entityManager, () -> entityManager.createQuery(String.format("DELETE FROM %s e WHERE e.id = :id", entityClass.getSimpleName()))
                .setParameter("id", id)
                .executeUpdate());
    }

    @Override
    public void deleteAll() {
        final EntityManager entityManager = getEntityManager();
        runInsideTransactionIfNotActive(entityManager, () -> entityManager.createQuery(String.format("DELETE FROM %s e", entityClass.getSimpleName())).executeUpdate());
    }

    @Override
    public long count() {
        final EntityManager entityManager = getEntityManager();
        return runInsideTransactionIfNotActive(entityManager, () -> entityManager.createQuery(String.format("SELECT COUNT(e) FROM %s e", entityClass.getSimpleName()), Long.class).getSingleResult());
    }

    @Override
    public boolean existsById(KEY id) {
        return getEntityManager().createQuery(String.format("SELECT COUNT(e) FROM %s e WHERE e.id = :id", entityClass.getSimpleName()), Long.class)
                .setParameter("id", id)
                .getSingleResult() > 0;
    }

    @Override
    public EntityManager getEntityManager() {
        final EntityManager contextEntityManager = PersistenceContext.getEntityManager();

        return contextEntityManager != null ? contextEntityManager : entityManagerFactory.createEntityManager();
    }

    private <R> R runInsideTransactionIfNotActive(EntityManager entityManager, Callable<R> callable) {

        try {
            if (!entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().begin();
            }

            final R call = callable.call();

            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().commit();
            }
            return call;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().commit();
            }
        }

    }

    private void runInsideTransactionIfNotActive(EntityManager entityManager, Runnable runnable) {
        if (!entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().begin();
        }

        runnable.run();

        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().commit();
        }
    }
}
