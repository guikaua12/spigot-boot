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
package tech.guilhermekaua.spigotboot.data.jdbc.repository.sql;

import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.ColumnMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;

import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

public class CrudSqlGenerator {
    private final Dialect dialect;

    public CrudSqlGenerator(Dialect dialect) {
        this.dialect = dialect;
    }

    public String insertSql(EntityMetadata metadata, boolean skipIdentityId) {
        return insertSql(metadata, skipIdentityId, Collections.emptyList());
    }

    public String insertSql(EntityMetadata metadata, boolean skipIdentityId, List<String> additionalColumns) {
        List<ColumnMetadata> columns = metadata.getColumns();
        StringJoiner columnNames = new StringJoiner(", ");
        StringJoiner placeholders = new StringJoiner(", ");

        for (ColumnMetadata col : columns) {
            if (skipIdentityId && col.isId()) {
                continue;
            }
            columnNames.add(dialect.quoteIdentifier(col.getColumnName()));
            placeholders.add("?");
        }

        for (String additionalColumn : additionalColumns) {
            columnNames.add(dialect.quoteIdentifier(additionalColumn));
            placeholders.add("?");
        }

        return "INSERT INTO " + dialect.quoteIdentifier(metadata.getTableName()) +
                " (" + columnNames + ") VALUES (" + placeholders + ")";
    }

    public String insertSql(EntityMetadata metadata) {
        return insertSql(metadata, false);
    }

    public String updateSql(EntityMetadata metadata) {
        List<ColumnMetadata> nonIdColumns = metadata.getNonIdColumns();
        StringJoiner setClauses = new StringJoiner(", ");

        for (ColumnMetadata col : nonIdColumns) {
            setClauses.add(dialect.quoteIdentifier(col.getColumnName()) + " = ?");
        }

        return "UPDATE " + dialect.quoteIdentifier(metadata.getTableName()) +
                " SET " + setClauses +
                " WHERE " + idWhereClause(metadata);
    }

    public String selectByIdSql(EntityMetadata metadata) {
        return "SELECT * FROM " + dialect.quoteIdentifier(metadata.getTableName()) +
                " WHERE " + idWhereClause(metadata);
    }

    public String selectAllSql(EntityMetadata metadata) {
        return "SELECT * FROM " + dialect.quoteIdentifier(metadata.getTableName());
    }

    public String deleteSql(EntityMetadata metadata) {
        return "DELETE FROM " + dialect.quoteIdentifier(metadata.getTableName()) +
                " WHERE " + idWhereClause(metadata);
    }

    public String deleteAllSql(EntityMetadata metadata) {
        return "DELETE FROM " + dialect.quoteIdentifier(metadata.getTableName());
    }

    public String countSql(EntityMetadata metadata) {
        return "SELECT COUNT(*) FROM " + dialect.quoteIdentifier(metadata.getTableName());
    }

    public String existsByIdSql(EntityMetadata metadata) {
        return "SELECT 1 FROM " + dialect.quoteIdentifier(metadata.getTableName()) +
                " WHERE " + idWhereClause(metadata) + " LIMIT 1";
    }

    public String upsertSql(EntityMetadata metadata) {
        return dialect.upsertSql(metadata);
    }

    private String idWhereClause(EntityMetadata metadata) {
        List<ColumnMetadata> idColumns = metadata.getIdMetadata().getColumns();
        StringJoiner joiner = new StringJoiner(" AND ");
        for (ColumnMetadata col : idColumns) {
            joiner.add(dialect.quoteIdentifier(col.getColumnName()) + " = ?");
        }
        return joiner.toString();
    }
}
