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

public class MySQLDialect implements Dialect {

    @Override
    public String quoteIdentifier(String name) {
        return "`" + name + "`";
    }

    @Override
    public String mapJavaTypeToSqlType(Class<?> javaType) {
        if (javaType == String.class || javaType == UUID.class
                || javaType == OffsetDateTime.class
                || javaType == OffsetTime.class
                || javaType == ZonedDateTime.class) return "VARCHAR(255)";
        if (javaType == BigDecimal.class) return "DECIMAL(38, 18)";
        if (javaType == BigInteger.class) return "DECIMAL(38, 0)";
        if (javaType == int.class || javaType == Integer.class) return "INT";
        if (javaType == long.class || javaType == Long.class) return "BIGINT";
        if (javaType == double.class || javaType == Double.class) return "DOUBLE";
        if (javaType == float.class || javaType == Float.class) return "FLOAT";
        if (javaType == boolean.class || javaType == Boolean.class) return "BOOLEAN";
        if (javaType == byte[].class) return "BLOB";
        if (javaType == Instant.class || javaType == LocalDateTime.class
                || javaType == Date.class
                || javaType == Calendar.class) return "DATETIME";
        if (javaType == LocalDate.class) return "DATE";
        if (javaType == LocalTime.class) return "TIME";
        if (javaType == Duration.class) return "BIGINT";
        if (javaType == short.class || javaType == Short.class) return "SMALLINT";
        if (javaType == byte.class || javaType == Byte.class) return "TINYINT";
        return "VARCHAR(255)";
    }

    @Override
    public String autoIncrementClause() {
        return "AUTO_INCREMENT";
    }

    @Override
    public String upsertSql(EntityMetadata metadata) {
        List<ColumnMetadata> columns = metadata.getColumns();
        List<ColumnMetadata> nonIdColumns = metadata.getNonIdColumns();

        StringJoiner columnNames = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");
        StringJoiner updates = new StringJoiner(", ");

        for (ColumnMetadata col : columns) {
            columnNames.add(quoteIdentifier(col.getColumnName()));
            placeholders.add("?");
        }

        for (ColumnMetadata col : nonIdColumns) {
            updates.add(quoteIdentifier(col.getColumnName()) + " = VALUES(" + quoteIdentifier(col.getColumnName()) + ")");
        }

        return "INSERT INTO " + quoteIdentifier(metadata.getTableName()) +
                " (" + columnNames + ") VALUES (" + placeholders + ")" +
                " ON DUPLICATE KEY UPDATE " + updates;
    }

    @Override
    public String paginationSql(String baseSql, int limit, int offset) {
        return baseSql + " LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public int maxBindParameters() {
        return 65535;
    }

    @Override
    public int defaultPoolSize() {
        return Runtime.getRuntime().availableProcessors() * 2 + 1;
    }

    @Override
    public void configureDataSource(HikariDataSource ds) {
        ds.setMaximumPoolSize(defaultPoolSize());
        ds.setMinimumIdle(Math.min(defaultPoolSize(), 10));
        ds.setMaxLifetime(1800000);
        ds.setConnectionTimeout(10000);
        ds.setLeakDetectionThreshold(10000);
    }
}
