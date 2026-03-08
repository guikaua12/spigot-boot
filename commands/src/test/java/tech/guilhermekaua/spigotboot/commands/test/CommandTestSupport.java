package tech.guilhermekaua.spigotboot.commands.test;

import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

final class CommandTestSupport {
    private static final TestCommandPlatformSupport PLATFORM_SUPPORT = new TestCommandPlatformSupport();

    private CommandTestSupport() {
    }

    static TestCommandPlatformSupport platformSupport() {
        return PLATFORM_SUPPORT;
    }

    static TestCommandSenderHandle senderHandleFor(TestSender sender) {
        return new TestCommandSenderHandle(sender);
    }

    static TestSender testSender(String name, String... permissions) {
        return new TestSender(name, permissions);
    }

    static TestPlayer testPlayer(String name, UUID uniqueId, String... permissions) {
        return new TestPlayer(name, uniqueId, permissions);
    }

    static TestBootPlugin testPlugin() {
        return new TestBootPlugin();
    }
}

final class TestCommandPlatformSupport implements CommandPlatformSupport {
    @Override
    public CommandSenderHandle createSender(Object nativeSender) {
        if (!(nativeSender instanceof TestSender)) {
            throw new IllegalArgumentException("Unsupported native sender type: " + nativeSender);
        }
        return new TestCommandSenderHandle((TestSender) nativeSender);
    }

    @Override
    public boolean isSenderType(Class<?> type) {
        return TestSender.class.isAssignableFrom(type);
    }
}

final class TestCommandSenderHandle implements CommandSenderHandle {
    private final TestSender sender;

    TestCommandSenderHandle(TestSender sender) {
        this.sender = sender;
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public String getIdentity() {
        return sender.getIdentity();
    }

    @Override
    public boolean hasPermission(String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public void sendMessage(String message) {
        sender.sendMessage(message);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        if (!type.isInstance(sender)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(sender));
    }
}

class TestSender {
    private final String name;
    private final Set<String> permissions;
    private final List<String> messages = new ArrayList<>();

    TestSender(String name, String... permissions) {
        this.name = name;
        this.permissions = new LinkedHashSet<>(Arrays.asList(permissions));
    }

    String getName() {
        return name;
    }

    String getIdentity() {
        return name;
    }

    boolean hasPermission(String permission) {
        return permissions.contains("*") || permissions.contains(permission);
    }

    void sendMessage(String message) {
        messages.add(message);
    }

    List<String> getMessages() {
        return messages;
    }
}

final class TestPlayer extends TestSender {
    private final UUID uniqueId;

    TestPlayer(String name, UUID uniqueId, String... permissions) {
        super(name, permissions);
        this.uniqueId = uniqueId;
    }

    UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    String getIdentity() {
        return uniqueId.toString();
    }
}

final class TestBootPlugin implements BootPlugin {
    @Override
    public String getName() {
        return "TestPlugin";
    }

    @Override
    public Logger getLogger() {
        return Logger.getLogger("TestPlugin");
    }

    @Override
    public File getDataFolder() {
        return new File(".");
    }

    @Override
    public InputStream getResource(String path) {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    @Override
    public Class<?> getMainClass() {
        return getClass();
    }

    @Override
    public Object getNativePlugin() {
        return null;
    }
}
