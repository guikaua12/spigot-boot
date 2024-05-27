/*
 * MIT License
 *
 * Copyright (c) 2023 Guilherme Kauã (Approximations)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.approximations.apxPlugin.messaging.bungee.actions.responseable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import me.approximations.apxPlugin.messaging.bungee.BungeeChannel;
import me.approximations.apxPlugin.messaging.bungee.actions.ResponseableMessageAction;
import me.approximations.apxPlugin.messaging.bungee.handler.MessageResponseHandler;
import me.approximations.apxPlugin.messaging.message.Message;
import me.approximations.apxPlugin.messaging.message.MessageResponse;
import me.approximations.apxPlugin.messaging.utils.ChannelDataOutputUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@EqualsAndHashCode(callSuper=true)
@Data
public class ForwardAction<I extends Serializable, O extends Serializable> extends ResponseableMessageAction<MessageResponse<O>> {
    public static final String SUB_CHANNEL = "Forward";
    public static final String SERVER_ALL = "ALL";
    public static final String SERVER_ONLINE = "ONLINE";

    private final @NotNull String server;
    private final @NotNull String subChannel;
    private final @NotNull I i;
    private final @NotNull Plugin plugin;

    public @NotNull String getSubChannel() {
        return SUB_CHANNEL;
    }

    @Override
    public CompletableFuture<MessageResponse<O>> sendMessage(@Nullable Player player, @NotNull Plugin plugin, @NotNull MessageResponseHandler<Object, MessageResponse<O>> responseHandler) throws IOException {
        final Message<I, O> message = new Message<>(UUID.randomUUID(), server, subChannel, i);
        ChannelDataOutputUtils.sendMessage(player, plugin, BungeeChannel.NAME,
                output -> {
                    output.writeUTF(SUB_CHANNEL);
                    output.writeUTF(server);
                    output.writeUTF(subChannel);
                },
                output -> {
                    try {
                        output.writeObject(message);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        return responseHandler.addFuture(message.getId());
    }

    public void respond(MessageResponse<O> response) {
        try {
            ChannelDataOutputUtils.sendMessage(null, plugin, BungeeChannel.NAME,
                    output -> {
                        output.writeUTF(SUB_CHANNEL);
                        output.writeUTF(server);
                        output.writeUTF(subChannel);
                    },
                    output -> {
                        try {
                            output.writeObject(response);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
