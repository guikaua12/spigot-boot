/*
 * MIT License
 *
 * Copyright (c) 2023 Guilherme Kauã (Approximations)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.approximations.spigotboot.messaging.bungee.handler;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.util.concurrent.CompletableFuture;

public interface MessageResponseHandler<I, O> {
    void handle(@NotNull DataInput dataInput);

    CompletableFuture<O> addFuture(@NotNull I key);

    default CompletableFuture<O> addFuture() {
        if (getInputClass() != Void.class)
            throw new UnsupportedOperationException("This method is not supported for this handler, you mostly likely is trying to use it for a handler that requires a key. Use addFuture(I key) instead.");

        return addFuture(null);
    }

    @NotNull Class<I> getInputClass();

    @NotNull Class<O> getOutputClass();
}
