/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.messaging.utils;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.messaging.bungee.BungeeChannel;

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
