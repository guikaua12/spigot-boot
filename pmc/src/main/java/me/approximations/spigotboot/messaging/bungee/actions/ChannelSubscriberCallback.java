package me.approximations.spigotboot.messaging.bungee.actions;

import me.approximations.spigotboot.messaging.message.Message;

import java.io.Serializable;

public interface ChannelSubscriberCallback<I extends Serializable, O extends Serializable> {
    void onMessage(Message<I, O> message);
}
