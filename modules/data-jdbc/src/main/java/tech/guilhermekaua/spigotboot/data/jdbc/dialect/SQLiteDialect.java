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
package tech.guilhermekaua.spigotboot.data.jdbc.dialect;

import com.zaxxer.hikari.HikariDataSource;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.ColumnMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;

public class SQLiteDialect implements Dialect {

    @Override
    public String quoteIdentifier(String name) {
        return "\"" + name + "\"";
    }

    @Override
    public String mapJavaTypeToSqlType(Class<?> javaType) {
        if (javaType == String.class || javaType == UUID.class
                || javaType == OffsetDateTime.class
                || javaType == OffsetTime.class
                || javaType == ZonedDateTime.class) return "TEXT";
        if (javaType == BigDecimal.class || javaType == BigInteger.class) return "NUMERIC";
        if (javaType == int.class || javaType == Integer.class) return "INTEGER";
        if (javaType == long.class || javaType == Long.class) return "INTEGER";
        if (javaType == double.class || javaType == Double.class) return "REAL";
        if (javaType == float.class || javaType == Float.class) return "REAL";
        if (javaType == boolean.class || javaType == Boolean.class) return "INTEGER";
        if (javaType == byte[].class) return "BLOB";
        if (javaType == Instant.class || javaType == LocalDateTime.class
                || javaType == Date.class
                || javaType == Calendar.class) return "TEXT";
        if (javaType == LocalDate.class) return "TEXT";
        if (javaType == LocalTime.class) return "TEXT";
        if (javaType == Duration.class) return "INTEGER";
        if (javaType == short.class || javaType == Short.class) return "INTEGER";
        if (javaType == byte.class || javaType == Byte.class) return "INTEGER";
        return "TEXT";
    }

    @Override
    public String autoIncrementClause() {
        return "AUTOINCREMENT";
    }

    @Override
    public String upsertSql(EntityMetadata metadata) {
        // sqlite 3.7.2 does not support ON CONFLICT DO UPDATE, use INSERT OR REPLACE
        List<ColumnMetadata> columns = metadata.getColumns();

        StringJoiner columnNames = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");

        for (ColumnMetadata col : columns) {
            columnNames.add(quoteIdentifier(col.getColumnName()));
            placeholders.add("?");
        }

        return "INSERT OR REPLACE INTO " + quoteIdentifier(metadata.getTableName()) +
                " (" + columnNames + ") VALUES (" + placeholders + ")";
    }

    @Override
    public String paginationSql(String baseSql, int limit, int offset) {
        return baseSql + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public int maxBindParameters() {
        return 999;
    }

    @Override
    public int defaultPoolSize() {
        return 1;
    }

    @Override
    public void configureDataSource(HikariDataSource ds) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
        ds.setMaximumPoolSize(1);
        ds.setConnectionTestQuery("SELECT 1");
        // journal_mode must not be set as a DataSource property because
        // SQLiteConfig.apply() runs all properties via executeBatch(),
        // and PRAGMA journal_mode returns a result set, which old SQLite
        // JDBC drivers (e.g. the one bundled with PaperSpigot 1.8.8) reject
        // with "batch entry 0: query returns results".
        // connectionInitSql runs via Statement.execute() after connection
        // creation, which handles result-returning PRAGMAs correctly.
        ds.setConnectionInitSql("PRAGMA journal_mode=WAL");
        ds.addDataSourceProperty("foreign_keys", "ON");
    }
}
