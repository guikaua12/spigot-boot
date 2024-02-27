package me.approximations.apxPlugin.messaging.bungee;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.ApxPlugin;
import me.approximations.apxPlugin.di.annotations.Inject;
import me.approximations.apxPlugin.messaging.Channel;
import me.approximations.apxPlugin.messaging.bungee.actions.NormalMessageAction;
import me.approximations.apxPlugin.messaging.bungee.actions.ResponseableMessageAction;
import me.approximations.apxPlugin.messaging.bungee.actions.responseable.*;
import me.approximations.apxPlugin.messaging.bungee.handler.MessageResponseHandler;
import me.approximations.apxPlugin.messaging.bungee.handler.impl.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@NoArgsConstructor(force=true)
public class BungeeChannel implements Channel, PluginMessageListener {
    public static final String NAME = "BungeeCord";

    private final Map<String, MessageResponseHandler<?, ?>> responseHandlerMap = new HashMap<>();
    @NotNull @Inject
    private final ApxPlugin plugin;

    @Override
    public void init() {
        registerChannel();
        plugin.addDisableEntry(this::unregisterChannel);

        responseHandlerMap.put(PlayerCountAction.SUB_CHANNEL, new PlayerCountHandler());
        responseHandlerMap.put(IpOtherAction.SUB_CHANNEL, new IpOtherHandler());
        responseHandlerMap.put(PlayerListAction.SUB_CHANNEL, new PlayerListHandler());
        responseHandlerMap.put(GetPlayerServerAction.SUB_CHANNEL, new GetPlayerServerHandler());
        responseHandlerMap.put(GetServersAction.SUB_CHANNEL, new GetServersHandler());
        responseHandlerMap.put(GetServerAction.SUB_CHANNEL, new GetServerHandler());
    }

    private void registerChannel() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, NAME);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, NAME, this);
    }

    private void unregisterChannel() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public void sendMessage(Player player, NormalMessageAction messageAction) {
        messageAction.sendMessage(player, this.plugin);
    }

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> sendMessage(Player player, ResponseableMessageAction<T> messageAction) {
        final MessageResponseHandler<?, ?> handler = responseHandlerMap.get(messageAction.getSubChannel());

        Objects.requireNonNull(handler, "Handler not found for sub channel " + messageAction.getSubChannel());

        return messageAction.sendMessage(player, this.plugin, (MessageResponseHandler<Object, T>) handler);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] bytes) {
        if (!channel.equals(NAME)) return;

        final ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
        final String subChannel = in.readUTF();

        final MessageResponseHandler<?, ?> handler = responseHandlerMap.get(subChannel);
        if (handler != null) {
            handler.handle(in);
        }
    }
}
