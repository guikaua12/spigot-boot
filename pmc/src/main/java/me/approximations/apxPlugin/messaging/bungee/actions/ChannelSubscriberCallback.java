package me.approximations.apxPlugin.messaging.bungee.actions;

import me.approximations.apxPlugin.messaging.message.Message;

import java.io.Serializable;

public interface ChannelSubscriberCallback<I extends Serializable, O extends Serializable> {
    void onMessage(Message<I, O> message);
}
