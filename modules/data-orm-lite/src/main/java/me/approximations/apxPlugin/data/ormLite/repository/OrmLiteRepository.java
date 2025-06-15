package me.approximations.apxPlugin.data.ormLite.repository;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import me.approximations.apxPlugin.data.repository.Repository;

public interface OrmLiteRepository<T, ID> extends Repository<T, ID> {
    QueryBuilder<T, ID> queryBuilder();

    UpdateBuilder<T, ID> updateBuilder();

    DeleteBuilder<T, ID> deleteBuilder();

    Dao.CreateOrUpdateStatus createOrUpdate(T var1);

    int update(T var1);
}
