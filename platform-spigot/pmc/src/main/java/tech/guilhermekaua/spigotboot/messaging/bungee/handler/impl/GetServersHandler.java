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
package tech.guilhermekaua.spigotboot.messaging.bungee.handler.impl;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.messaging.bungee.handler.MessageResponseHandler;

import java.io.DataInput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
public class GetServersHandler implements MessageResponseHandler<Void, List<String>> {
    private static final List<String> EMPTY_LIST = Collections.emptyList();

    private final Queue<CompletableFuture<List<String>>> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void handle(@NotNull DataInput in) {
        try {
            final String[] serverList = in.readUTF().split(", ");

            final CompletableFuture<List<String>> future = queue.poll();

            if (future == null) return;

            future.complete(Arrays.asList(serverList));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<List<String>> addFuture(@Nullable Void key) {
        final CompletableFuture<List<String>> cf = new CompletableFuture<>();
        queue.add(cf);
        return cf;
    }

    @Override
    public @NotNull Class<Void> getInputClass() {
        return Void.class;
    }

    @Override
    public @NotNull Class<List<String>> getOutputClass() {
        return (Class<List<String>>) EMPTY_LIST.getClass();
    }

}
