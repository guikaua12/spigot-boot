package me.approximations.apxPlugin.data.ormLite.repository.impl;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.data.ormLite.repository.OrmLiteRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class OrmLiteRepositoryImpl<T, ID> implements OrmLiteRepository<T, ID> {
    private final Dao<T, ID> dao;

    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");

        try {
            dao.create(entity);
            return entity;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create entity", e);
        }
    }

    @Override
    public List<T> saveAll(Iterable<T> iterable) {
        Objects.requireNonNull(iterable, "Iterable cannot be null");

        List<T> toAdd = new ArrayList<>();

        if (iterable instanceof Collection) {
            toAdd.addAll((Collection<? extends T>) iterable);
        } else {
            for (T entity : iterable) {
                toAdd.add(entity);
            }
        }

        if (toAdd.isEmpty()) {
            return toAdd;
        }

        try {
            dao.create(toAdd);
            return toAdd;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create entities", e);
        }
    }


    @Override
    public T findById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        try {
            return dao.queryForId(id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find entity by ID: " + id, e);
        }
    }

    @Override
    public List<T> findAll() {
        try {
            return dao.queryForAll();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all entities", e);
        }
    }

    @Override
    public void delete(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");

        try {
            dao.delete(entity);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete entity", e);
        }
    }

    @Override
    public void delete(Iterable<T> iterable) {
        Objects.requireNonNull(iterable, "Iterable cannot be null");

        List<T> toDelete = new ArrayList<>();

        if (iterable instanceof Collection) {
            toDelete.addAll((Collection<? extends T>) iterable);
        } else {
            for (T entity : iterable) {
                toDelete.add(entity);
            }
        }

        if (toDelete.isEmpty()) {
            return;
        }

        try {
            dao.delete(toDelete);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete entity", e);
        }
    }

    @Override
    public void deleteById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        try {
            dao.deleteById(id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete entity by ID: " + id, e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            dao.deleteBuilder().delete();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all entities", e);
        }
    }

    @Override
    public long count() {
        try {
            return dao.countOf();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count entities", e);
        }
    }

    @Override
    public boolean existsById(ID id) {
        Objects.requireNonNull(id, "ID cannot be null");

        try {
            return dao.idExists(id);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existence by ID: " + id, e);
        }
    }

    @Override
    public QueryBuilder<T, ID> queryBuilder() {
        return dao.queryBuilder();
    }

    @Override
    public UpdateBuilder<T, ID> updateBuilder() {
        return dao.updateBuilder();
    }

    @Override
    public DeleteBuilder<T, ID> deleteBuilder() {
        return dao.deleteBuilder();
    }

    @Override
    public Dao.CreateOrUpdateStatus createOrUpdate(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");

        try {
            return dao.createOrUpdate(entity);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create or update entity", e);
        }
    }

    @Override
    public int update(T entity) {
        Objects.requireNonNull(entity, "Entity cannot be null");

        try {
            return dao.update(entity);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update entity", e);
        }
    }
}
