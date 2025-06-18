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
package me.approximations.spigotboot.messaging.bungee.handler.impl;

import lombok.Getter;
import me.approximations.spigotboot.messaging.bungee.handler.MessageResponseHandler;
import me.approximations.spigotboot.messaging.message.MessageResponse;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
public class ForwardHandler implements MessageResponseHandler<UUID, MessageResponse<?>> {
    @SuppressWarnings({"unchecked", "InstantiatingObjectToGetClassObject"}) private static final Class<MessageResponse<?>> OUTPUT_CLAZZ = (Class<MessageResponse<?>>) new MessageResponse<>().getClass();


    private final Map<UUID, Queue<CompletableFuture<MessageResponse<?>>>> map = new HashMap<>();

    @Override
    public void handle(@NotNull DataInput in) {
    }

    @Override
    public CompletableFuture<MessageResponse<?>> addFuture(@NotNull UUID key) {
        final Queue<CompletableFuture<MessageResponse<?>>> queue = map.computeIfAbsent(key, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());

        final CompletableFuture<MessageResponse<?>> cf = new CompletableFuture<>();
        queue.add(cf);

        return cf;
    }

    @Override
    public @NotNull Class<UUID> getInputClass() {
        return UUID.class;
    }

    @Override
    public @NotNull Class<MessageResponse<?>> getOutputClass() {
        return OUTPUT_CLAZZ;
    }

    public CompletableFuture<MessageResponse<?>> pollFuture(@NotNull UUID key) {
        final Queue<CompletableFuture<MessageResponse<?>>> queue = map.get(key);
        if (queue == null) return null;

        final CompletableFuture<MessageResponse<?>> cf = queue.poll();
        if (queue.isEmpty()) map.remove(key);

        return cf;
    }


}
