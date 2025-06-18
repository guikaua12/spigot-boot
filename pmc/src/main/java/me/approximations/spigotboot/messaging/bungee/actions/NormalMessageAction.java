package me.approximations.spigotboot.messaging.bungee.actions;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class NormalMessageAction implements MessageAction {
    public abstract void sendMessage(@Nullable Player player, @NotNull Plugin plugin) throws IOException;
}
