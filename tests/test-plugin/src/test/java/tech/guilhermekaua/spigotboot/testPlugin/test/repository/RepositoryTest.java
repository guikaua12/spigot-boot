/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.testPlugin.test.repository;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.testPlugin.Main;
import tech.guilhermekaua.spigotboot.testPlugin.People;
import tech.guilhermekaua.spigotboot.testPlugin.repositories.UserRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class RepositoryTest {
    private ServerMock server;
    private Main plugin;

    private UserRepository userRepository;

    @BeforeEach
    public void setUp() throws SQLException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);

        userRepository = plugin.getDependencyManager().resolveDependency(UserRepository.class);

        ConnectionSource connectionSource = plugin.getDependencyManager().resolveDependency(ConnectionSource.class);
        TableUtils.createTableIfNotExists(connectionSource, People.class);
        TableUtils.clearTable(connectionSource, People.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void shouldInsert() throws SQLException {
        final People people = new People(UUID.randomUUID(), "test", "test@gmail.com", Instant.now());
        Assertions.assertNull(userRepository.findById(people.getUuid()));

        userRepository.save(people);

        Assertions.assertNotNull(userRepository.findById(people.getUuid()));
    }

    @Test
    public void shouldFindById() {
        final People people = new People(UUID.randomUUID(), "test", "test@test.com", Instant.now());
        userRepository.save(people);

        final People foundPeople = userRepository.findById(people.getUuid());
        Assertions.assertNotNull(foundPeople);
        Assertions.assertEquals(people.getUuid(), foundPeople.getUuid());
    }

    @Test
    public void shouldFindUsingCustomMethod() throws SQLException {
        final People people = new People(UUID.randomUUID(), "test", "test@test.com", Instant.now());
        userRepository.save(people);

        // findByEmail is a custom method on UserRepository
        final People foundPeople = userRepository.findByEmail(people.getEmail());
        Assertions.assertNotNull(foundPeople);
        Assertions.assertEquals(people.getEmail(), foundPeople.getEmail());
    }

    @Test
    public void shouldFindAll() {
        Instant now = Instant.now();
        final People people1 = new People(UUID.randomUUID(), "test1", "test1@test.com", now);
        final People people2 = new People(UUID.randomUUID(), "test2", "test2@test.com", now);
        final People people3 = new People(UUID.randomUUID(), "test3", "test3@test.com", now);
        userRepository.saveAll(Arrays.asList(people1, people2, people3));

        final List<People> peoples = userRepository.findAll();
        Assertions.assertNotNull(peoples);
        Assertions.assertEquals(3, peoples.size());

        Assertions.assertEquals(people1.getUuid(), peoples.get(0).getUuid());
        Assertions.assertEquals(people2.getUuid(), peoples.get(1).getUuid());
        Assertions.assertEquals(people3.getUuid(), peoples.get(2).getUuid());
    }

    @Test
    public void shouldDelete() {
        final People people = new People(UUID.randomUUID(), "test", "test@test.com", Instant.now());
        userRepository.save(people);

        Assertions.assertEquals(1, userRepository.count());
        userRepository.delete(people);
        Assertions.assertEquals(0, userRepository.count());
    }

    @Test
    public void shouldDeleteById() {
        final People people = new People(UUID.randomUUID(), "test", "test@test.com", Instant.now());
        userRepository.save(people);

        Assertions.assertEquals(1, userRepository.count());
        userRepository.deleteById(people.getUuid());
        Assertions.assertEquals(0, userRepository.count());
    }

    @Test
    public void shouldDeleteAll() {
        final People people1 = new People(UUID.randomUUID(), "test1", "test@test.com", Instant.now());
        final People people2 = new People(UUID.randomUUID(), "test2", "test@test.com", Instant.now());
        final People people3 = new People(UUID.randomUUID(), "test3", "test@test.com", Instant.now());
        userRepository.save(people1);
        userRepository.save(people2);
        userRepository.save(people3);

        Assertions.assertEquals(3, userRepository.count());
        userRepository.deleteAll();
        Assertions.assertEquals(0, userRepository.count());
    }

    @Test
    public void shouldCount() {
        final People people1 = new People(UUID.randomUUID(), "test1", "test@test.com", Instant.now());
        final People people2 = new People(UUID.randomUUID(), "test2", "test@test.com", Instant.now());
        final People people3 = new People(UUID.randomUUID(), "test3", "test@test.com", Instant.now());
        userRepository.save(people1);
        userRepository.save(people2);
        userRepository.save(people3);

        Assertions.assertEquals(3, userRepository.count());
    }

    @Test
    public void shouldExistsById() {
        final People people = new People(UUID.randomUUID(), "test", "test@test.com", Instant.now());
        userRepository.save(people);

        Assertions.assertTrue(userRepository.existsById(people.getUuid()));

        userRepository.delete(people);

        Assertions.assertFalse(userRepository.existsById(people.getUuid()));
    }
}
