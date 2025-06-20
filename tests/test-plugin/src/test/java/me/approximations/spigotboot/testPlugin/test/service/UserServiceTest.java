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
package me.approximations.spigotboot.testPlugin.test.service;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import me.approximations.spigotboot.testPlugin.Main;
import me.approximations.spigotboot.testPlugin.People;
import me.approximations.spigotboot.testPlugin.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

public class UserServiceTest {
    private ServerMock server;
    private Main plugin;
    private UserService userService;

    @BeforeEach
    public void setUp() throws SQLException {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);

        userService = plugin.getDependencyManager().resolveDependency(UserService.class);
        ConnectionSource connectionSource = plugin.getDependencyManager().resolveDependency(ConnectionSource.class);
        TableUtils.createTableIfNotExists(connectionSource, People.class);
        TableUtils.clearTable(connectionSource, People.class);
    }

    @Test
    public void test() throws InterruptedException {

        System.out.println(Thread.currentThread().getName());
        userService.getPeople(UUID.randomUUID())
                .thenAccept(peopleOptional -> {
                    System.out.println(Thread.currentThread().getName());
                    System.out.println(peopleOptional);
                }).exceptionally(throwable -> {
                    throwable.printStackTrace();
                    throw new RuntimeException(throwable);
                });
        Thread.sleep(5000);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }
}
