package me.approximations.apxPlugin.testPlugin.repositories;

import me.approximations.apxPlugin.persistence.jpa.repository.impl.SimpleJpaRepository;
import me.approximations.apxPlugin.testPlugin.People;
import org.hibernate.Session;

public class UserRepository extends SimpleJpaRepository<People, Long> {
    private final String name = "UserRepository";

    public UserRepository(Session entityManager, Class<People> entityClass) {
        super(entityManager, entityClass);
    }
}
