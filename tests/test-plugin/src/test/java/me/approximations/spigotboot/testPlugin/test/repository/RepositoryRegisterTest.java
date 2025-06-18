package me.approximations.spigotboot.testPlugin.test.repository;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.approximations.spigotboot.testPlugin.Main;
import me.approximations.spigotboot.testPlugin.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RepositoryRegisterTest {
    private ServerMock server;
    private Main plugin;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testRepositoryIsRegistered() {
        UserRepository userRepository = plugin.getDependencyManager().resolveDependency(UserRepository.class);
        Assertions.assertNotNull(userRepository, "UserRepository should be registered and injected");
        // Optionally, test repository functionality
        Assertions.assertDoesNotThrow(() -> userRepository.findAll());
    }
}

