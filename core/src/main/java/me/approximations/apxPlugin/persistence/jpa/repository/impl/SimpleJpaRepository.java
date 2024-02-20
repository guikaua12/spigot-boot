package me.approximations.apxPlugin.persistence.jpa.repository.impl;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.persistence.jpa.repository.JpaRepository;
import org.hibernate.Session;

import java.util.List;

@RequiredArgsConstructor
public class SimpleJpaRepository<T, KEY> implements JpaRepository<T, KEY> {
    protected final Session entityManager;
    private final Class<T> entityClass;

    @Override
    public T save(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Override
    public T findById(KEY id) {
        return entityManager.find(entityClass, id);
    }

    @Override
    public List<T> findAll() {
        return entityManager.createQuery(String.format("SELECT e FROM %s e", entityClass.getSimpleName()), entityClass).getResultList();
    }

    @Override
    public void delete(T entity) {
        entityManager.createQuery(String.format("DELETE FROM %s e WHERE e = :entity", entityClass.getSimpleName()))
                .setParameter("entity", entity)
                .executeUpdate();
    }

    @Override
    public void deleteById(KEY id) {
        entityManager.createQuery(String.format("DELETE FROM %s e WHERE e.id = :id", entityClass.getSimpleName()))
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public void deleteAll() {
        entityManager.createQuery(String.format("DELETE FROM %s e", entityClass.getSimpleName())).executeUpdate();
    }

    @Override
    public long count() {
        return entityManager.createQuery(String.format("SELECT COUNT(e) FROM %s e", entityClass.getSimpleName()), Long.class).getSingleResult();
    }

    @Override
    public boolean existsById(KEY id) {
        return entityManager.createQuery(String.format("SELECT COUNT(e) FROM %s e WHERE e.id = :id", entityClass.getSimpleName()), Long.class)
                .setParameter("id", id)
                .getSingleResult() > 0;
    }
}
