package me.approximations.spigotboot.messaging.utils;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.approximations.spigotboot.messaging.bungee.BungeeChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.function.Consumer;

public final class ChannelDataOutputUtils {
    public static final Consumer<ObjectOutputStream> EMPTY_BODY = dataOutput -> {
    };

    @SuppressWarnings("UnstableApiUsage")
    public static ByteArrayDataOutput write(Consumer<ByteArrayDataOutput> header, Consumer<ObjectOutputStream> body) throws IOException {
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();

        header.accept(out);

        final ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        final ObjectOutputStream msgout = new ObjectOutputStream(msgbytes);

        body.accept(msgout);

        out.writeShort(msgbytes.toByteArray().length);
        out.write(msgbytes.toByteArray());

        return out;
    }

    public static ByteArrayDataOutput write(Consumer<ByteArrayDataOutput> header) throws IOException {
        return write(header, EMPTY_BODY);
    }

    public static void sendMessage(@Nullable Player player, @NotNull Plugin plugin, @NotNull String channel, Consumer<ByteArrayDataOutput> header, Consumer<ObjectOutputStream> body) throws IllegalStateException, IOException {
        final ByteArrayDataOutput out = ChannelDataOutputUtils.write(header, body);

        if (player == null) player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);

        if (player == null) {
            throw new IllegalStateException("No online player found to send the message");
        }

        player.sendPluginMessage(plugin, BungeeChannel.NAME, out.toByteArray());
    }

    public static void sendMessage(@Nullable Player player, @NotNull Plugin plugin, @NotNull String channel, Consumer<ByteArrayDataOutput> header) throws IllegalStateException, IOException {
        sendMessage(player, plugin, channel, header, EMPTY_BODY);
    }

}
