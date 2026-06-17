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
package tech.guilhermekaua.spigotboot.data.jdbc.repository;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Column;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Id;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.IdStrategy;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Table;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.BuiltInConverters;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.ddl.DdlGenerator;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.SQLiteDialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataParser;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.impl.JdbcRepositoryImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcRepositorySqlTemporalRoundTripTest {
    private Path databaseFile;
    private HikariDataSource dataSource;
    private ConnectionProvider connectionProvider;
    private JdbcRepositoryImpl<SqlTemporalRow, Long> repository;

    @BeforeEach
    void setUp() throws Exception {
        databaseFile = Files.createTempFile("data-jdbc-sql-temporal", ".db");

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + toSqlitePath(databaseFile));
        dataSource.setMaximumPoolSize(1);

        connectionProvider = new ConnectionProvider(dataSource);
        Dialect dialect = new SQLiteDialect();

        TypeConverterRegistry converterRegistry = new TypeConverterRegistry();
        BuiltInConverters.registerAll(converterRegistry);

        EntityMetadataRegistry metadataRegistry = new EntityMetadataRegistry(new EntityMetadataParser(converterRegistry));
        EntityMetadata metadata = metadataRegistry.getOrParse(SqlTemporalRow.class);

        DdlGenerator ddlGenerator = new DdlGenerator(dialect, metadataRegistry);
        createTable(ddlGenerator.generateCreateTable(metadata));

        repository = new JdbcRepositoryImpl<>(connectionProvider, metadata, dialect, metadataRegistry);
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

    @Test
    void roundTripsSqlTemporalFieldsThroughSqlite() {
        Timestamp createdAt = Timestamp.valueOf("2026-03-03 12:15:45");
        java.sql.Date eventDate = java.sql.Date.valueOf("2026-03-03");
        Time eventTime = Time.valueOf("09:05:07");

        SqlTemporalRow row = new SqlTemporalRow();
        row.setCreatedAt(createdAt);
        row.setEventDate(eventDate);
        row.setEventTime(eventTime);

        repository.insert(row);

        List<SqlTemporalRow> rows = repository.findAll();
        assertEquals(1, rows.size());

        SqlTemporalRow loaded = rows.get(0);
        assertEquals(createdAt, loaded.getCreatedAt());
        assertEquals(eventDate, loaded.getEventDate());
        assertEquals(eventTime, loaded.getEventTime());
    }

    private void createTable(String ddl) throws Exception {
        try (Connection connection = connectionProvider.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private String toSqlitePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    @Table("sql_temporal_rows")
    public static final class SqlTemporalRow {
        @Id(strategy = IdStrategy.IDENTITY)
        @Column("id")
        private Long id;

        @Column("created_at")
        private Timestamp createdAt;

        @Column("event_date")
        private java.sql.Date eventDate;

        @Column("event_time")
        private Time eventTime;

        public SqlTemporalRow() {
        }

        public Long getId() {
            return id;
        }

        public Timestamp getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Timestamp createdAt) {
            this.createdAt = createdAt;
        }

        public java.sql.Date getEventDate() {
            return eventDate;
        }

        public void setEventDate(java.sql.Date eventDate) {
            this.eventDate = eventDate;
        }

        public Time getEventTime() {
            return eventTime;
        }

        public void setEventTime(Time eventTime) {
            this.eventTime = eventTime;
        }
    }
}
