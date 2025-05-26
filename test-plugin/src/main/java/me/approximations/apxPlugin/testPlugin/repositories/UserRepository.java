package me.approximations.apxPlugin.testPlugin.repositories;

import me.approximations.apxPlugin.persistence.jpa.repository.Query;
import me.approximations.apxPlugin.persistence.jpa.repository.Repository;
import me.approximations.apxPlugin.testPlugin.People;

import java.util.UUID;

public interface UserRepository extends Repository<People, UUID> {
    @Query("SELECT u FROM People u WHERE u.uuid = :uuid")
    People findById(String uuid);
}
