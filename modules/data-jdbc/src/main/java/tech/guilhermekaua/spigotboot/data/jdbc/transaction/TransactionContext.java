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
package tech.guilhermekaua.spigotboot.data.jdbc.transaction;

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;

public final class TransactionContext {
    private static final ThreadLocal<TransactionState> CURRENT_STATE = new ThreadLocal<>();

    private TransactionContext() {
    }

    public static void bind(Connection connection) {
        CURRENT_STATE.set(new TransactionState(connection));
    }

    public static void unbind() {
        CURRENT_STATE.remove();
    }

    public static @Nullable Connection getCurrent() {
        TransactionState state = CURRENT_STATE.get();
        return state == null ? null : state.connection;
    }

    public static boolean hasTransaction() {
        return CURRENT_STATE.get() != null;
    }

    public static void incrementDepth() {
        TransactionState state = requireState();
        state.depth++;
    }

    public static void decrementDepth() {
        TransactionState state = requireState();
        state.depth--;
    }

    public static int getDepth() {
        TransactionState state = requireState();
        return state.depth;
    }

    public static void markRollbackOnly() {
        TransactionState state = requireState();
        state.rollbackOnly = true;
    }

    public static boolean isRollbackOnly() {
        TransactionState state = CURRENT_STATE.get();
        return state != null && state.rollbackOnly;
    }

    private static TransactionState requireState() {
        TransactionState state = CURRENT_STATE.get();
        if (state == null) {
            throw new IllegalStateException("No active transaction context");
        }
        return state;
    }

    private static final class TransactionState {
        private final Connection connection;
        private int depth;
        private boolean rollbackOnly;

        private TransactionState(Connection connection) {
            this.connection = connection;
            this.depth = 1;
            this.rollbackOnly = false;
        }
    }
}
