package me.approximations.apxPlugin.messaging.bungee;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.messaging.Channel;
import me.approximations.apxPlugin.messaging.bungee.actions.ChannelSubscriberCallback;
import me.approximations.apxPlugin.messaging.bungee.actions.NormalMessageAction;
import me.approximations.apxPlugin.messaging.bungee.actions.ResponseableMessageAction;
import me.approximations.apxPlugin.messaging.bungee.actions.responseable.*;
import me.approximations.apxPlugin.messaging.bungee.handler.MessageResponseHandler;
import me.approximations.apxPlugin.messaging.bungee.handler.impl.*;
import me.approximations.apxPlugin.messaging.message.Message;
import me.approximations.apxPlugin.messaging.message.MessageResponse;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class BungeeChannel implements Channel, PluginMessageListener {
    public static final String NAME = "BungeeCord";

    private final Map<String, MessageResponseHandler<?, ?>> responseHandlerMap = new HashMap<>();
    private final Map<String, List<ChannelSubscriberCallback>> subscriberMap = new HashMap<>();
    @NotNull
    private final Plugin plugin;

    @Override
    public void init() {
        registerChannel();

        responseHandlerMap.put(PlayerCountAction.SUB_CHANNEL, new PlayerCountHandler());
        responseHandlerMap.put(IpOtherAction.SUB_CHANNEL, new IpOtherHandler());
        responseHandlerMap.put(PlayerListAction.SUB_CHANNEL, new PlayerListHandler());
        responseHandlerMap.put(GetPlayerServerAction.SUB_CHANNEL, new GetPlayerServerHandler());
        responseHandlerMap.put(GetServersAction.SUB_CHANNEL, new GetServersHandler());
        responseHandlerMap.put(GetServerAction.SUB_CHANNEL, new GetServerHandler());
        responseHandlerMap.put(ForwardAction.SUB_CHANNEL, new ForwardHandler());
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

    public void sendMessage(Player player, NormalMessageAction messageAction) throws IOException {
        messageAction.sendMessage(player, this.plugin);
    }

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> sendMessage(Player player, ResponseableMessageAction<T> messageAction) throws IOException {
        final MessageResponseHandler<?, ?> handler = responseHandlerMap.get(messageAction.getSubChannel());

        Objects.requireNonNull(handler, "Handler not found for sub channel " + messageAction.getSubChannel());

        return messageAction.sendMessage(player, this.plugin, (MessageResponseHandler<Object, T>) handler);
    }

    @SuppressWarnings("unchecked")
    public <O extends Serializable> CompletableFuture<MessageResponse<O>> sendMessage(Player player, ForwardAction<?, O> forwardAction) throws IOException {
        return forwardAction.sendMessage(player, this.plugin, (MessageResponseHandler<Object, MessageResponse<O>>) responseHandlerMap.get(ForwardAction.SUB_CHANNEL));
    }

    public <I extends Serializable, O extends Serializable> void subscribe(String subChannel, ChannelSubscriberCallback<I, O> callback) {
        subscriberMap.computeIfAbsent(subChannel, k -> new ArrayList<>()).add(callback);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] bytes) {
        if (!channel.equals(NAME)) return;

        final ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
        final String subChannel = in.readUTF();

        final MessageResponseHandler<?, ?> handler = responseHandlerMap.get(subChannel);
        if (handler != null) {
            handler.handle(in);
            return;
        }

        final List<ChannelSubscriberCallback> callbacks = subscriberMap.get(subChannel);

        if (callbacks == null) return;

        final short len = in.readShort();
        final byte[] msgbytes = new byte[len];
        in.readFully(msgbytes);

        try {
            final ObjectInputStream msgin = new ObjectInputStream(new ByteArrayInputStream(msgbytes));

            final Object object = msgin.readObject();

            if (object instanceof Message) {
                for (ChannelSubscriberCallback callback : callbacks) {
                    callback.onMessage((Message) object);
                }
                return;
            }

            final MessageResponse<?> response = (MessageResponse<?>) object;
            final ForwardHandler forwardHandler = (ForwardHandler) responseHandlerMap.get(ForwardAction.SUB_CHANNEL);

            final CompletableFuture<MessageResponse<?>> cf = forwardHandler.pollFuture(response.getResponseId());
            cf.complete(response);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }


    }
}
