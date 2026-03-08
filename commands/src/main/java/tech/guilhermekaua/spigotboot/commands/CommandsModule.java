package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.module.Module;

public class CommandsModule implements Module {
    @Override
    public void onInitialize(Context context) {
        context.getPlugin().getLogger().fine("Initializing commands module.");
    }
}
