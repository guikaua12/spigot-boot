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
package tech.guilhermekaua.spigotboot.data.jdbc.connection;

import tech.guilhermekaua.spigotboot.data.jdbc.transaction.TransactionContext;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionProvider {
    private static final Logger FALLBACK_LOGGER = Logger.getLogger(ConnectionProvider.class.getName());

    private final DataSource dataSource;
    private final Logger logger;
    private final boolean showSql;

    public ConnectionProvider(DataSource dataSource) {
        this(dataSource, FALLBACK_LOGGER, false);
    }

    public ConnectionProvider(DataSource dataSource, Logger logger, boolean showSql) {
        this.dataSource = dataSource;
        this.logger = logger == null ? FALLBACK_LOGGER : logger;
        this.showSql = showSql;
    }

    public Connection getConnection() throws SQLException {
        Connection txConnection = TransactionContext.getCurrent();
        if (txConnection != null) {
            return connectionHandle(txConnection, true);
        }

        Connection connection = dataSource.getConnection();
        if (!showSql) {
            return connection;
        }

        return connectionHandle(connection, false);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    private Connection connectionHandle(Connection connection, boolean suppressClose) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> invokeConnection(connection, method, args, suppressClose)
        );
    }

    private Object invokeConnection(Connection connection, Method method, Object[] args, boolean suppressClose) throws Throwable {
        if (suppressClose && isCloseMethod(method)) {
            return null;
        }

        try {
            if (showSql && isPrepareStatementMethod(method)) {
                PreparedStatement preparedStatement = (PreparedStatement) method.invoke(connection, args);
                return preparedStatementHandle(preparedStatement, resolveSqlArgument(args, null));
            }

            if (showSql && isCreateStatementMethod(method)) {
                Statement statement = (Statement) method.invoke(connection, args);
                return statementHandle(statement);
            }

            return method.invoke(connection, args);
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    private Statement statementHandle(Statement statement) {
        List<String> batchedSql = new ArrayList<>();
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class[]{Statement.class},
                (proxy, method, args) -> invokeStatement(statement, method, args, null, batchedSql)
        );
    }

    private PreparedStatement preparedStatementHandle(PreparedStatement preparedStatement, String sql) {
        List<String> batchedSql = new ArrayList<>();
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                (proxy, method, args) -> invokeStatement(preparedStatement, method, args, sql, batchedSql)
        );
    }

    private Object invokeStatement(
            Statement statement,
            Method method,
            Object[] args,
            String preparedSql,
            List<String> batchedSql
    ) throws Throwable {
        if (isAddBatchWithSql(method, args)) {
            batchedSql.add((String) args[0]);
        }

        if (isClearBatchMethod(method)) {
            batchedSql.clear();
        }

        boolean executeBatch = isExecuteBatchMethod(method);
        if (executeBatch) {
            logBatchSql(preparedSql, batchedSql);
        } else if (isExecuteSqlMethod(method)) {
            logSql(resolveSqlArgument(args, preparedSql));
        }

        try {
            return method.invoke(statement, args);
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        } finally {
            if (executeBatch) {
                batchedSql.clear();
            }
        }
    }

    private boolean isPrepareStatementMethod(Method method) {
        return "prepareStatement".equals(method.getName())
                && PreparedStatement.class.isAssignableFrom(method.getReturnType());
    }

    private boolean isCreateStatementMethod(Method method) {
        return "createStatement".equals(method.getName())
                && Statement.class.isAssignableFrom(method.getReturnType());
    }

    private boolean isExecuteSqlMethod(Method method) {
        String name = method.getName();
        return "execute".equals(name)
                || "executeQuery".equals(name)
                || "executeUpdate".equals(name)
                || "executeLargeUpdate".equals(name);
    }

    private boolean isExecuteBatchMethod(Method method) {
        String name = method.getName();
        return "executeBatch".equals(name) || "executeLargeBatch".equals(name);
    }

    private boolean isAddBatchWithSql(Method method, Object[] args) {
        return "addBatch".equals(method.getName())
                && args != null
                && args.length == 1
                && args[0] instanceof String;
    }

    private boolean isClearBatchMethod(Method method) {
        return "clearBatch".equals(method.getName()) && method.getParameterCount() == 0;
    }

    private void logBatchSql(String preparedSql, List<String> batchedSql) {
        if (batchedSql.isEmpty()) {
            logSql(preparedSql);
            return;
        }

        for (String sql : batchedSql) {
            logSql(sql);
        }
    }

    private String resolveSqlArgument(Object[] args, String fallbackSql) {
        if (args != null && args.length > 0 && args[0] instanceof String) {
            return (String) args[0];
        }

        return fallbackSql;
    }

    private void logSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }

        logger.log(Level.INFO, "data-jdbc sql: {0}", sql);
    }

    private boolean isCloseMethod(Method method) {
        return "close".equals(method.getName()) && method.getParameterCount() == 0;
    }
}
