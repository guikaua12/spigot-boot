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
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

@Getter
public class IpOtherHandler implements MessageResponseHandler<String, InetSocketAddress> {

    private final Map<String, Queue<CompletableFuture<InetSocketAddress>>> map = new HashMap<>();

    @Override
    public void handle(@NotNull DataInput in) {
        try {
            final String userName = in.readUTF();
            final String address = in.readUTF();
            final int port = in.readInt();

            final Queue<CompletableFuture<InetSocketAddress>> queue = map.get(userName);

            if (queue == null) return;

            final CompletableFuture<InetSocketAddress> future = queue.poll();

            if (future == null) return;

            future.complete(new InetSocketAddress(address, port));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<InetSocketAddress> addFuture(@NotNull String key) {
        final Queue<CompletableFuture<InetSocketAddress>> queue = map.computeIfAbsent(key, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
        final CompletableFuture<InetSocketAddress> cf = new CompletableFuture<>();

        queue.add(cf);

        return cf;
    }

    @Override
    public @NotNull Class<String> getInputClass() {
        return String.class;
    }

    @Override
    public @NotNull Class<InetSocketAddress> getOutputClass() {
        return InetSocketAddress.class;
    }

}
