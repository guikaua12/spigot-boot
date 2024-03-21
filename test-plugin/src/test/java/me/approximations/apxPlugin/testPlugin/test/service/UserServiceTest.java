package me.approximations.apxPlugin.testPlugin.test.service;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.approximations.apxPlugin.testPlugin.Main;
import me.approximations.apxPlugin.testPlugin.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserServiceTest {
    private ServerMock server;
    private Main plugin;
    private UserService userService;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);

        userService = plugin.getDependencyManager().getDependency(UserService.class);
    }

    @Test
    public void test() throws InterruptedException {
        userService.getPeople("Some uuid")
                .thenAccept(peopleOptional -> {
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
