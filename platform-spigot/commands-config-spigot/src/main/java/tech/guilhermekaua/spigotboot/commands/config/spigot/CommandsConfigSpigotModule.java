package tech.guilhermekaua.spigotboot.commands.config.spigot;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.module.Module;

public class CommandsConfigSpigotModule implements Module {
    @Override
    public void onInitialize(Context context) {
        context.getPlugin().getLogger().fine("Initializing commands config bridge module.");
    }
}
