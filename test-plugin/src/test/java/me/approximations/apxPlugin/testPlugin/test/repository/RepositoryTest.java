package me.approximations.apxPlugin.testPlugin.test.repository;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.approximations.apxPlugin.testPlugin.Main;
import me.approximations.apxPlugin.testPlugin.People;
import me.approximations.apxPlugin.testPlugin.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class RepositoryTest {
    private ServerMock server;
    private Main plugin;

    private UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);

        userRepository = plugin.getDependencyManager().resolveDependency(UserRepository.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void shouldInsert() {
        final People people = new People(UUID.randomUUID(), "test", "test@gmail.com", Instant.now());
        Assertions.assertNull(userRepository.findById(people.getUuid()));

        userRepository.save(people);

        Assertions.assertNotNull(userRepository.findById(people.getUuid()));
    }

    @Test
    public void shouldFindById() {
        final People people = new People(null, "test", "test@test.com", Instant.now());
        userRepository.save(people);

        final People foundPeople = userRepository.findById(people.getUuid());
        Assertions.assertNotNull(foundPeople);
        Assertions.assertEquals(people.getUuid(), foundPeople.getUuid());
    }

    @Test
    public void shouldFindByIdCustomMethod() {
        final People people = new People(null, "test", "test@test.com", Instant.now());
        userRepository.save(people);

        // findById is a custom method on UserRepository
        final People foundPeople = userRepository.findById(people.getUuid());
        Assertions.assertNotNull(foundPeople);
        Assertions.assertEquals(people.getUuid(), foundPeople.getUuid());
    }

    @Test
    public void shouldFindAll() {
        final People people1 = new People(null, "test1", "test1@test.com", Instant.now());
        final People people2 = new People(null, "test2", "test2@test.com", Instant.now());
        final People people3 = new People(null, "test3", "test3@test.com", Instant.now());
        userRepository.save(people1);
        userRepository.save(people2);
        userRepository.save(people3);

        final List<People> people = userRepository.findAll();
        Assertions.assertNotNull(people);
        Assertions.assertEquals(3, people.size());
        Assertions.assertEquals(people, Arrays.asList(people1, people2, people3));
    }

    @Test
    public void shouldDelete() {
        final People people = new People(null, "test", "test@test.com", Instant.now());
        userRepository.save(people);

        Assertions.assertEquals(1, userRepository.count());
        userRepository.delete(people);
        Assertions.assertEquals(0, userRepository.count());
    }

    @Test
    public void shouldDeleteById() {
        final People people = new People(null, "test", "test@test.com", Instant.now());
        userRepository.save(people);

        Assertions.assertEquals(1, userRepository.count());
        userRepository.deleteById(people.getUuid());
        Assertions.assertEquals(0, userRepository.count());
    }

    @Test
    public void shouldDeleteAll() {
        final People people1 = new People(null, "test1", "test@test.com", Instant.now());
        final People people2 = new People(null, "test2", "test@test.com", Instant.now());
        final People people3 = new People(null, "test3", "test@test.com", Instant.now());
        userRepository.save(people1);
        userRepository.save(people2);
        userRepository.save(people3);

        Assertions.assertEquals(3, userRepository.count());
        userRepository.deleteAll();
        Assertions.assertEquals(0, userRepository.count());
    }

    @Test
    public void shouldCount() {
        final People people1 = new People(null, "test1", "test@test.com", Instant.now());
        final People people2 = new People(null, "test2", "test@test.com", Instant.now());
        final People people3 = new People(null, "test3", "test@test.com", Instant.now());
        userRepository.save(people1);
        userRepository.save(people2);
        userRepository.save(people3);

        Assertions.assertEquals(3, userRepository.count());
    }

    @Test
    public void shouldExistsById() {
        final People people = new People(null, "test", "test@test.com", Instant.now());
        userRepository.save(people);

        Assertions.assertTrue(userRepository.existsById(people.getUuid()));

        userRepository.delete(people);

        Assertions.assertFalse(userRepository.existsById(people.getUuid()));
    }
}
