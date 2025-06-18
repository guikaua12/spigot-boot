package me.approximations.spigotboot.testPlugin.test.service;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.google.common.reflect.ClassPath;
import me.approximations.spigotboot.testPlugin.Main;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class GuiceTest {
    private ServerMock server;
    private Main plugin;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);
    }

    @Test
    public void test() {
        try {
            final ClassPath classPath = ClassPath.from(ClassLoader.getSystemClassLoader());
            classPath.getAllClasses()
                    .stream()
                    .filter(clazz -> clazz.getPackageName().startsWith(Main.class.getPackageName()))
                    .forEach(clazz -> System.out.println(clazz.getName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

}
