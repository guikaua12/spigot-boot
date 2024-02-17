package me.approximations.apxPlugin.persistence.jpa.repository;

import java.util.List;

public interface CrudRepository<T, KEY> extends Repository<T, KEY> {
    T save(T entity);

    T findById(KEY id);

    List<T> findAll();

    void delete(T entity);

    void deleteById(KEY id);

    void deleteAll();

    long count();

    boolean existsById(KEY id);
}
