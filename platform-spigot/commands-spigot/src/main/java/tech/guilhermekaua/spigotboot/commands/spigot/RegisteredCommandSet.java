package tech.guilhermekaua.spigotboot.commands.spigot;

import java.util.Collections;
import java.util.List;

public final class RegisteredCommandSet {
    private final List<SpigotBootCommand> commands;

    public RegisteredCommandSet(List<SpigotBootCommand> commands) {
        this.commands = Collections.unmodifiableList(commands);
    }

    public List<SpigotBootCommand> getCommands() {
        return commands;
    }
}
