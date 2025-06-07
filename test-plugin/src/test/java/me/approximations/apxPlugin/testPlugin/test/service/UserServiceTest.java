package me.approximations.apxPlugin.testPlugin.test.service;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import me.approximations.apxPlugin.testPlugin.Main;
import me.approximations.apxPlugin.testPlugin.People;
import me.approximations.apxPlugin.testPlugin.services.UserService;
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
