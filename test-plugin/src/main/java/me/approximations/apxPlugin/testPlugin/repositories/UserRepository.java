package me.approximations.apxPlugin.testPlugin.repositories;

import me.approximations.apxPlugin.persistence.jpa.repository.impl.SimpleJpaRepository;
import me.approximations.apxPlugin.testPlugin.People;

import javax.persistence.EntityManagerFactory;

public class UserRepository extends SimpleJpaRepository<People, Long> {
    private final String name = "UserRepository";

    public UserRepository(EntityManagerFactory entityManagerFactory, Class<People> entityClass) {
        super(entityManagerFactory, entityClass);
    }
}
