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
package tech.guilhermekaua.spigotboot.messaging.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.messaging.bungee.BungeeChannel;
import tech.guilhermekaua.spigotboot.messaging.bungee.actions.responseable.ForwardAction;
import tech.guilhermekaua.spigotboot.messaging.utils.ChannelDataOutputUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor(force = true)
@AllArgsConstructor
@Data
public class Message<I extends Serializable, O extends Serializable> implements Serializable {
    @NotNull
    private final UUID id;
    @NotNull
    private final String originServer;
    @NotNull
    private final String subChannel;
    @NotNull
    private final I body;

    public void respond(O body, Plugin plugin) {
        final MessageResponse<O> response = new MessageResponse<>(id, body);
        try {
            ChannelDataOutputUtils.sendMessage(null, plugin, BungeeChannel.NAME,
                    output -> {
                        output.writeUTF(ForwardAction.SUB_CHANNEL);
                        output.writeUTF(originServer);
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
