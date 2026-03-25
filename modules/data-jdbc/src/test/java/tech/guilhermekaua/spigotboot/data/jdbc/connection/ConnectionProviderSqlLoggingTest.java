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

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionProviderSqlLoggingTest {
    private Path databaseFile;
    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        databaseFile = Files.createTempFile("data-jdbc-show-sql", ".db");

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + toSqlitePath(databaseFile));
        dataSource.setMaximumPoolSize(1);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource != null) {
            dataSource.close();
        }

        if (databaseFile != null) {
            Files.deleteIfExists(databaseFile);
        }
    }

    @Nested
    class SqlLoggingTests {
        @Test
        void logsExecutedSqlWhenEnabled() throws Exception {
            Logger logger = Logger.getLogger("data-jdbc-show-sql-test-" + UUID.randomUUID());
            CapturingHandler handler = new CapturingHandler();
            attachHandler(logger, handler);

            try {
                ConnectionProvider connectionProvider = new ConnectionProvider(dataSource, logger, true);
                executeSampleQueries(connectionProvider);
            } finally {
                logger.removeHandler(handler);
            }

            assertTrue(handler.messages.stream().anyMatch(message -> message.contains("CREATE TABLE players")));
            assertTrue(handler.messages.stream().anyMatch(message -> message.contains("INSERT INTO players(name) VALUES (?)")));
            assertTrue(handler.messages.stream().anyMatch(message -> message.contains("SELECT name FROM players WHERE name = ?")));
        }

        @Test
        void doesNotLogSqlWhenDisabled() throws Exception {
            Logger logger = Logger.getLogger("data-jdbc-hide-sql-test-" + UUID.randomUUID());
            CapturingHandler handler = new CapturingHandler();
            attachHandler(logger, handler);

            try {
                ConnectionProvider connectionProvider = new ConnectionProvider(dataSource, logger, false);
                executeSampleQueries(connectionProvider);
            } finally {
                logger.removeHandler(handler);
            }

            assertEquals(0, handler.messages.size());
        }
    }

    private void attachHandler(Logger logger, CapturingHandler handler) {
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    private void executeSampleQueries(ConnectionProvider connectionProvider) throws Exception {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE players (id INTEGER PRIMARY KEY, name TEXT)");
        }

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO players(name) VALUES (?)")) {
            statement.setString(1, "alex");
            statement.executeUpdate();
        }

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT name FROM players WHERE name = ?")) {
            statement.setString(1, "alex");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("alex", resultSet.getString("name"));
            }
        }
    }

    private String toSqlitePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }

            Object[] parameters = record.getParameters();
            if (parameters == null || parameters.length == 0) {
                messages.add(record.getMessage());
                return;
            }

            messages.add(MessageFormat.format(record.getMessage(), parameters));
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
