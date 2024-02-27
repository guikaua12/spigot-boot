package me.approximations.apxPlugin.messaging.bungee.actions;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NormalMessageAction implements MessageAction {
    public abstract void sendMessage(@Nullable Player player, @NotNull Plugin plugin);
}
