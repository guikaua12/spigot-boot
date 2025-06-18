package me.approximations.spigotboot.messaging.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.approximations.spigotboot.messaging.bungee.BungeeChannel;
import me.approximations.spigotboot.messaging.bungee.actions.responseable.ForwardAction;
import me.approximations.spigotboot.messaging.utils.ChannelDataOutputUtils;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor(force=true)
@AllArgsConstructor
@Data
public class Message<I extends Serializable, O extends Serializable> implements Serializable {
    @NotNull private final UUID id;
    @NotNull private final String originServer;
    @NotNull private final String subChannel;
    @NotNull private final I body;

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
