package me.approximations.apxPlugin.utils;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.approximations.apxPlugin.messaging.bungee.BungeeChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.util.function.Consumer;

public final class ChannelDataOutputUtils {
    public static final Consumer<DataOutput> EMPTY_BODY = dataOutput -> {
    };

    @SuppressWarnings("UnstableApiUsage")
    public static ByteArrayDataOutput write(Consumer<ByteArrayDataOutput> header, Consumer<DataOutput> body) {
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();

        header.accept(out);

        final ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        final DataOutputStream msgout = new DataOutputStream(msgbytes);

        body.accept(msgout);

        out.writeShort(msgbytes.toByteArray().length);
        out.write(msgbytes.toByteArray());

        return out;
    }

    public static ByteArrayDataOutput write(Consumer<ByteArrayDataOutput> header) {
        return write(header, EMPTY_BODY);
    }

    public static void sendMessage(@Nullable Player player, @NotNull Plugin plugin, @NotNull String channel, Consumer<ByteArrayDataOutput> header, Consumer<DataOutput> body) throws IllegalStateException {
        final ByteArrayDataOutput out = ChannelDataOutputUtils.write(header, body);

        if (player == null) player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);

        if (player == null) {
            throw new IllegalStateException("No online player found to send the message");
        }

        player.sendPluginMessage(plugin, BungeeChannel.NAME, out.toByteArray());
    }

    public static void sendMessage(@Nullable Player player, @NotNull Plugin plugin, @NotNull String channel, Consumer<ByteArrayDataOutput> header) throws IllegalStateException {
        sendMessage(player, plugin, channel, header, EMPTY_BODY);
    }

}
