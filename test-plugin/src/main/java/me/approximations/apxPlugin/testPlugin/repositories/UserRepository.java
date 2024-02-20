package me.approximations.apxPlugin.testPlugin.repositories;

import me.approximations.apxPlugin.persistence.jpa.repository.impl.SimpleJpaRepository;
import me.approximations.apxPlugin.testPlugin.People;

import javax.persistence.EntityManager;

public class UserRepository extends SimpleJpaRepository<People, Long> {
    private final String name = "UserRepository";

    public UserRepository(EntityManager entityManager, Class<People> entityClass) {
        super(entityManager, entityClass);
    }
}
