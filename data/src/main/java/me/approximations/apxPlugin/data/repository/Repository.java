package me.approximations.apxPlugin.data.repository;

import java.util.List;

public interface Repository<T, KEY> {
    T save(T entity);

    List<T> saveAll(Iterable<T> iterable);

    T findById(KEY id);

    List<T> findAll();

    void delete(T entity);

    void delete(Iterable<T> iterable);

    void deleteById(KEY id);

    void deleteAll();

    long count();

    boolean existsById(KEY id);
}
