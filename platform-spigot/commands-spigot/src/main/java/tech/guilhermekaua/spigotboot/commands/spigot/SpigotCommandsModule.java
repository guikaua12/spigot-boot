package tech.guilhermekaua.spigotboot.commands.spigot;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.module.Module;

public class SpigotCommandsModule implements Module {
    @Override
    public void onInitialize(Context context) {
        context.getPlugin().getLogger().fine("Initializing Spigot commands module.");
    }
}
