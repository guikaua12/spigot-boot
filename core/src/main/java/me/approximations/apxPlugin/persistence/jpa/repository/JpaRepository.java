package me.approximations.apxPlugin.persistence.jpa.repository;

import javax.persistence.EntityManager;

public interface JpaRepository<T, KEY> extends CrudRepository<T, KEY> {
    EntityManager getEntityManager();
}
