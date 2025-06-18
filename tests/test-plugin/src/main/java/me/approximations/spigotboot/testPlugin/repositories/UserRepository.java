package me.approximations.spigotboot.testPlugin.repositories;

import me.approximations.spigotboot.data.ormLite.repository.OrmLiteRepository;
import me.approximations.spigotboot.testPlugin.People;

import java.sql.SQLException;
import java.util.UUID;

public interface UserRepository extends OrmLiteRepository<People, UUID> {
    default People findByEmail(String email) throws SQLException {
        return queryBuilder()
                .where()
                .eq("email", email)
                .queryForFirst();
    }
}
