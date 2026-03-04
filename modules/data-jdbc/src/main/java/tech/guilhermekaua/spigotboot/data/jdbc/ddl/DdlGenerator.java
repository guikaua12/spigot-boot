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
package tech.guilhermekaua.spigotboot.data.jdbc.ddl;

import tech.guilhermekaua.spigotboot.data.jdbc.annotation.IdStrategy;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public class DdlGenerator {
    private final Dialect dialect;
    private final EntityMetadataRegistry metadataRegistry;

    public DdlGenerator(Dialect dialect, EntityMetadataRegistry metadataRegistry) {
        this.dialect = dialect;
        this.metadataRegistry = metadataRegistry;
    }

    public String generateCreateTable(EntityMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append(dialect.quoteIdentifier(metadata.getTableName()));
        sb.append(" (");

        IdMetadata idMeta = metadata.getIdMetadata();
        List<ColumnMetadata> idColumns = idMeta.getColumns();
        boolean isSingleIdentityId = !idMeta.isComposite()
                && idMeta.getStrategy() == IdStrategy.IDENTITY
                && idColumns.size() == 1;

        StringJoiner columnDefs = new StringJoiner(", ");

        for (ColumnMetadata col : metadata.getColumns()) {
            StringBuilder colDef = new StringBuilder();
            colDef.append(dialect.quoteIdentifier(col.getColumnName()));
            colDef.append(" ");

            Class<?> javaType = col.getJavaType();
            if (col.getConverter() != null) {
                // when a converter is present, the db type depends on the converter's target type,
                // but for ddl we use the java type as-is since the converter handles the mapping
                colDef.append(dialect.mapJavaTypeToSqlType(javaType));
            } else {
                colDef.append(dialect.mapJavaTypeToSqlType(javaType));
            }

            if (isSingleIdentityId && col.isId()) {
                if (dialect instanceof tech.guilhermekaua.spigotboot.data.jdbc.dialect.SQLiteDialect) {
                    // sqlite: PRIMARY KEY must come with column definition for AUTOINCREMENT
                    colDef.append(" PRIMARY KEY ").append(dialect.autoIncrementClause());
                } else {
                    colDef.append(" ").append(dialect.autoIncrementClause());
                }
            }

            colDef.append(" NOT NULL");
            columnDefs.add(colDef.toString());
        }

        appendJoinColumns(metadata, columnDefs);

        // primary key constraint (not needed for single sqlite identity)
        boolean skipPkConstraint = isSingleIdentityId
                && dialect instanceof tech.guilhermekaua.spigotboot.data.jdbc.dialect.SQLiteDialect;

        if (!skipPkConstraint) {
            StringJoiner pkColumns = new StringJoiner(", ");
            for (ColumnMetadata idCol : idColumns) {
                pkColumns.add(dialect.quoteIdentifier(idCol.getColumnName()));
            }
            columnDefs.add("PRIMARY KEY (" + pkColumns + ")");
        }

        sb.append(columnDefs);
        sb.append(")");

        return sb.toString();
    }

    private void appendJoinColumns(EntityMetadata metadata, StringJoiner columnDefs) {
        Set<String> declaredColumns = new HashSet<>();
        for (ColumnMetadata column : metadata.getColumns()) {
            declaredColumns.add(column.getColumnName());
        }

        for (RelationshipMetadata relationship : metadata.getRelationships()) {
            if (relationship.getType() != RelationshipMetadata.RelationshipType.MANY_TO_ONE) {
                continue;
            }

            EntityMetadata targetMetadata = resolveTargetMetadata(relationship.getTargetEntityClass());
            for (RelationshipJoinColumn joinColumn : relationship.getJoinColumns()) {
                String localJoinColumnName = joinColumn.getColumnName();
                if (declaredColumns.contains(localJoinColumnName)) {
                    continue;
                }

                ColumnMetadata referencedColumn = resolveReferencedColumn(targetMetadata, joinColumn.getReferencedColumnName());
                String columnDefinition = dialect.quoteIdentifier(localJoinColumnName) + " " +
                        dialect.mapJavaTypeToSqlType(referencedColumn.getJavaType());
                columnDefs.add(columnDefinition);
                declaredColumns.add(localJoinColumnName);
            }
        }
    }

    private EntityMetadata resolveTargetMetadata(Class<?> targetEntityClass) {
        if (metadataRegistry == null) {
            throw new IllegalStateException(
                    "EntityMetadataRegistry is required for DDL join-column generation. " +
                            "Use DdlGenerator(dialect, metadataRegistry)."
            );
        }

        return metadataRegistry.getOrParse(targetEntityClass);
    }

    private ColumnMetadata resolveReferencedColumn(EntityMetadata targetMetadata, String referencedColumnName) {
        for (ColumnMetadata columnMetadata : targetMetadata.getColumns()) {
            if (columnMetadata.getColumnName().equals(referencedColumnName)) {
                return columnMetadata;
            }
        }

        throw new IllegalArgumentException(
                "Could not resolve referenced column '" + referencedColumnName + "' on entity " +
                        targetMetadata.getEntityClass().getName()
        );
    }
}
