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

import tech.guilhermekaua.spigotboot.data.transaction.TransactionCallback;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionCallbackWithoutResult;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class JdbcTransactionManager implements TransactionManager {
    private final DataSource dataSource;

    public JdbcTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        if (TransactionContext.hasTransaction()) {
            return executeNested(callback);
        }

        return executeOutermost(callback);
    }

    @Override
    public void execute(TransactionCallbackWithoutResult callback) {
        execute(() -> {
            callback.execute();
            return null;
        });
    }

    private <T> T executeNested(TransactionCallback<T> callback) {
        TransactionContext.incrementDepth();

        try {
            return callback.execute();
        } catch (Throwable throwable) {
            TransactionContext.markRollbackOnly();
            throw propagate(throwable);
        } finally {
            TransactionContext.decrementDepth();
        }
    }

    private <T> T executeOutermost(TransactionCallback<T> callback) {
        Connection connection = null;
        boolean previousAutoCommit = true;

        try {
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            TransactionContext.bind(connection);

            T result = callback.execute();

            if (TransactionContext.isRollbackOnly()) {
                connection.rollback();
                throw new IllegalStateException("Transaction was marked as rollback-only");
            }

            connection.commit();
            return result;
        } catch (Throwable throwable) {
            rollbackSafely(connection, throwable);
            throw propagate(throwable);
        } finally {
            TransactionContext.unbind();
            closeSafely(connection, previousAutoCommit);
        }
    }

    @Override
    public void executeWithoutResult(TransactionCallbackWithoutResult callback) {
        execute(callback);
    }

    private void rollbackSafely(Connection connection, Throwable throwable) {
        if (connection == null) {
            return;
        }

        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            throwable.addSuppressed(rollbackException);
        }
    }

    private void closeSafely(Connection connection, boolean previousAutoCommit) {
        if (connection == null) {
            return;
        }

        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ignored) {
            // ignore close cleanup failures
        }

        try {
            connection.close();
        } catch (SQLException ignored) {
            // ignore close cleanup failures
        }
    }

    private RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }

        if (throwable instanceof Error) {
            throw (Error) throwable;
        }

        return new RuntimeException("Transaction failed", throwable);
    }
}
