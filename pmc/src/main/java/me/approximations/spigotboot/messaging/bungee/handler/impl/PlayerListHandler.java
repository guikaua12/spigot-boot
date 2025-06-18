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
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Getter
public class PlayerListHandler implements MessageResponseHandler<String, List<String>> {
    private static final List<String> EMPTY_LIST = Collections.emptyList();

    private final Map<String, Queue<CompletableFuture<List<String>>>> map = new HashMap<>();

    @Override
    public void handle(@NotNull DataInput in) {
        try {
            final String server = in.readUTF();
            final String[] playerList = in.readUTF().split(", ");

            final Queue<CompletableFuture<List<String>>> queue = map.get(server);

            if (queue == null) return;

            final CompletableFuture<List<String>> future = queue.poll();

            if (future == null) return;

            future.complete(Arrays.asList(playerList));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<List<String>> addFuture(@NotNull String key) {
        final Queue<CompletableFuture<List<String>>> queue = map.computeIfAbsent(key, k -> new java.util.concurrent.ConcurrentLinkedQueue<>());

        final CompletableFuture<List<String>> cf = new CompletableFuture<>();
        queue.add(cf);
        return cf;
    }

    @Override
    public @NotNull Class<String> getInputClass() {
        return String.class;
    }

    @Override
    public @NotNull Class<List<String>> getOutputClass() {
        return (Class<List<String>>) EMPTY_LIST.getClass();
    }

}
