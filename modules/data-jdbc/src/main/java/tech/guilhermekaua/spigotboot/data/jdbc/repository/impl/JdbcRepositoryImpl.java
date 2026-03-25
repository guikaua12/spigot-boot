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
package tech.guilhermekaua.spigotboot.data.jdbc.repository.impl;

import tech.guilhermekaua.spigotboot.core.pagination.Page;
import tech.guilhermekaua.spigotboot.core.pagination.Pageable;
import tech.guilhermekaua.spigotboot.data.converter.AttributeConverter;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.IdStrategy;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.*;
import tech.guilhermekaua.spigotboot.data.jdbc.query.SelectQuery;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.JdbcRepository;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.mapper.EntityParameterBinder;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.mapper.EntityRowMapper;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.sql.CrudSqlGenerator;
import tech.guilhermekaua.spigotboot.data.persistable.Persistable;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

public class JdbcRepositoryImpl<T, ID> implements JdbcRepository<T, ID> {
    private final ConnectionProvider connectionProvider;
    private final EntityMetadata metadata;
    private final Dialect dialect;
    private final EntityMetadataRegistry metadataRegistry;
    private final CrudSqlGenerator sqlGenerator;
    private final EntityRowMapper<T> rowMapper;
    private final EntityParameterBinder parameterBinder;

    public JdbcRepositoryImpl(ConnectionProvider connectionProvider, EntityMetadata metadata, Dialect dialect, EntityMetadataRegistry metadataRegistry) {
        this.connectionProvider = connectionProvider;
        this.metadata = metadata;
        this.dialect = dialect;
        this.metadataRegistry = metadataRegistry;
        this.sqlGenerator = new CrudSqlGenerator(dialect);
        this.rowMapper = new EntityRowMapper<>(metadata);
        this.parameterBinder = new EntityParameterBinder(metadata);
    }

    @Override
    public T save(T entity) {
        if (isNew(entity)) {
            return insert(entity);
        }
        return update(entity);
    }

    @Override
    public T insert(T entity) {
        IdMetadata idMeta = metadata.getIdMetadata();

        // generate UUID if strategy is UUID and id is null
        if (!idMeta.isComposite() && idMeta.getStrategy() == IdStrategy.UUID) {
            Object currentId = parameterBinder.extractId(entity);
            if (currentId == null) {
                setIdField(entity, UUID.randomUUID());
            }
        }

        boolean isIdentity = !idMeta.isComposite() && idMeta.getStrategy() == IdStrategy.IDENTITY;
        List<InsertJoinColumnBinding> implicitJoinColumns = resolveImplicitManyToOneJoinColumns(entity);
        List<String> additionalColumns = new ArrayList<>(implicitJoinColumns.size());
        for (InsertJoinColumnBinding joinColumn : implicitJoinColumns) {
            additionalColumns.add(joinColumn.getColumnName());
        }

        String sql = sqlGenerator.insertSql(metadata, isIdentity, additionalColumns);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, isIdentity ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            int bindIndex = parameterBinder.bindInsertParameters(ps, entity, isIdentity);
            bindJoinColumnParameters(ps, implicitJoinColumns, bindIndex);
            ps.executeUpdate();

            if (isIdentity) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        Object generatedKey = keys.getObject(1);
                        ColumnMetadata idCol = idMeta.getColumns().get(0);
                        setFieldValue(idCol.getField(), entity, coerceIdType(generatedKey, idCol.getJavaType()));
                    }
                }
            }

            return entity;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert entity " + metadata.getEntityClass().getName(), e);
        }
    }

    private List<InsertJoinColumnBinding> resolveImplicitManyToOneJoinColumns(T entity) {
        Map<String, Object> resolvedValues = new LinkedHashMap<>();

        for (RelationshipMetadata relationship : metadata.getRelationships()) {
            if (relationship.getType() != RelationshipMetadata.RelationshipType.MANY_TO_ONE) {
                continue;
            }

            Object relatedEntity = getFieldValue(relationship.getField(), entity);
            if (relatedEntity == null) {
                continue;
            }

            EntityMetadata targetMetadata = metadataRegistry.getOrParse(relationship.getTargetEntityClass());
            for (RelationshipJoinColumn joinColumn : relationship.getJoinColumns()) {
                String localColumn = joinColumn.getColumnName();
                if (isMappedColumn(localColumn)) {
                    continue;
                }

                ColumnMetadata referencedColumn = findColumnMetadata(targetMetadata, joinColumn.getReferencedColumnName());
                Object referencedValue = readColumnValue(relatedEntity, targetMetadata, referencedColumn);
                if (referencedValue == null) {
                    throw new IllegalStateException(
                            "Cannot insert " + metadata.getEntityClass().getName() +
                                    " because relationship '" + relationship.getField().getName() +
                                    "' has null value for referenced column '" + joinColumn.getReferencedColumnName() + "'"
                    );
                }

                Object dbValue = convertForDatabase(referencedColumn, referencedValue);
                if (resolvedValues.containsKey(localColumn) && !resolvedValues.get(localColumn).equals(dbValue)) {
                    throw new IllegalStateException(
                            "Conflicting implicit join-column values for column '" + localColumn + "' on " +
                                    metadata.getEntityClass().getName()
                    );
                }

                resolvedValues.put(localColumn, dbValue);
            }
        }

        List<InsertJoinColumnBinding> joinColumns = new ArrayList<>(resolvedValues.size());
        for (Map.Entry<String, Object> entry : resolvedValues.entrySet()) {
            joinColumns.add(new InsertJoinColumnBinding(entry.getKey(), entry.getValue()));
        }

        return joinColumns;
    }

    private int bindJoinColumnParameters(
            PreparedStatement statement,
            List<InsertJoinColumnBinding> joinColumns,
            int startIndex
    ) throws SQLException {
        int index = startIndex;
        for (InsertJoinColumnBinding joinColumn : joinColumns) {
            statement.setObject(index++, joinColumn.getValue());
        }

        return index;
    }

    private boolean isMappedColumn(String columnName) {
        for (ColumnMetadata columnMetadata : metadata.getColumns()) {
            if (columnMetadata.getColumnName().equals(columnName)) {
                return true;
            }
        }

        return false;
    }

    private Object getFieldValue(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read field " + field.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object convertForDatabase(ColumnMetadata columnMetadata, Object value) {
        AttributeConverter<Object, Object> converter = (AttributeConverter<Object, Object>) columnMetadata.getConverter();
        if (converter == null || value == null) {
            return value;
        }

        return converter.convertToDatabaseColumn(value);
    }

    @Override
    public T update(T entity) {
        List<InsertJoinColumnBinding> implicitJoinColumns = resolveImplicitManyToOneJoinColumns(entity);
        List<String> additionalColumns = new ArrayList<>(implicitJoinColumns.size());
        for (InsertJoinColumnBinding joinColumn : implicitJoinColumns) {
            additionalColumns.add(joinColumn.getColumnName());
        }

        String sql = sqlGenerator.updateSql(metadata, additionalColumns);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int bindIndex = parameterBinder.bindUpdateParameters(ps, entity);
            bindJoinColumnParameters(ps, implicitJoinColumns, bindIndex);
            ps.executeUpdate();
            return entity;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update entity " + metadata.getEntityClass().getName(), e);
        }
    }

    @Override
    public List<T> saveAll(Iterable<T> iterable) {
        List<T> results = new ArrayList<>();
        for (T entity : iterable) {
            results.add(save(entity));
        }
        return results;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T findById(ID id) {
        String sql = sqlGenerator.selectByIdSql(metadata);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            parameterBinder.bindIdParameters(ps, id, 1);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowMapper.mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find entity by id", e);
        }
    }

    @Override
    public List<T> findAll() {
        String sql = sqlGenerator.selectAllSql(metadata);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rowMapper.mapRows(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all entities", e);
        }
    }

    private Object readColumnValue(Object entity, EntityMetadata entityMetadata, ColumnMetadata columnMetadata) {
        Field field = columnMetadata.getField();
        if (field.getDeclaringClass().isInstance(entity)) {
            return getFieldValue(field, entity);
        }

        if (!entityMetadata.getIdMetadata().isComposite()) {
            return getFieldValue(field, entity);
        }

        Object embeddedKey = getEmbeddedKeyValue(entity, entityMetadata);
        if (embeddedKey == null) {
            return null;
        }

        return getFieldValue(field, embeddedKey);
    }

    private Object getEmbeddedKeyValue(Object entity, EntityMetadata entityMetadata) {
        Class<?> embeddedKeyClass = entityMetadata.getIdMetadata().getEmbeddedKeyClass();
        if (embeddedKeyClass == null) {
            return null;
        }

        Class<?> current = entityMetadata.getEntityClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.isAnnotationPresent(tech.guilhermekaua.spigotboot.data.jdbc.annotation.EmbeddedId.class)) {
                    continue;
                }

                if (field.getType() != embeddedKeyClass) {
                    continue;
                }

                return getFieldValue(field, entity);
            }

            current = current.getSuperclass();
        }

        return null;
    }

    private ColumnMetadata findColumnMetadata(EntityMetadata sourceMetadata, String columnName) {
        for (ColumnMetadata columnMetadata : sourceMetadata.getColumns()) {
            if (columnMetadata.getColumnName().equals(columnName)) {
                return columnMetadata;
            }
        }

        throw new IllegalStateException(
                "Could not resolve referenced column '" + columnName + "' on entity " +
                        sourceMetadata.getEntityClass().getName()
        );
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        return select().fetchPage(pageable);
    }

    @Override
    public void delete(T entity) {
        Object id = parameterBinder.extractId(entity);
        deleteById0(id);
    }

    @Override
    public void delete(Iterable<T> iterable) {
        for (T entity : iterable) {
            delete(entity);
        }
    }

    @Override
    public void deleteById(ID id) {
        deleteById0(id);
    }

    private void deleteById0(Object id) {
        String sql = sqlGenerator.deleteSql(metadata);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            parameterBinder.bindIdParameters(ps, id, 1);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete entity by id", e);
        }
    }

    @Override
    public void deleteAll() {
        String sql = sqlGenerator.deleteAllSql(metadata);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all entities", e);
        }
    }

    @Override
    public long count() {
        String sql = sqlGenerator.countSql(metadata);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count entities", e);
        }
    }

    @Override
    public boolean existsById(ID id) {
        String sql = sqlGenerator.existsByIdSql(metadata);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            parameterBinder.bindIdParameters(ps, id, 1);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check existence by id", e);
        }
    }

    private boolean isNew(T entity) {
        if (entity instanceof Persistable<?>) {
            return ((Persistable<?>) entity).isNew();
        }

        IdMetadata idMeta = metadata.getIdMetadata();
        if (idMeta.isComposite()) {
            Object embeddedKey = parameterBinder.extractId(entity);
            if (embeddedKey == null) {
                return true;
            }

            for (ColumnMetadata idColumn : idMeta.getColumns()) {
                Object idColumnValue = getFieldValue(idColumn.getField(), embeddedKey);
                if (idColumnValue == null) {
                    return true;
                }
            }

            return false;
        }

        Object id = parameterBinder.extractId(entity);
        if (id == null) {
            return true;
        }

        // for primitive types with IDENTITY strategy, treat default values as "new"
        if (idMeta.getStrategy() == IdStrategy.IDENTITY) {
            if (id instanceof Number) {
                return ((Number) id).longValue() == 0;
            }
        }

        return false;
    }

    private void setIdField(Object entity, Object value) {
        IdMetadata idMeta = metadata.getIdMetadata();
        if (!idMeta.isComposite()) {
            ColumnMetadata idCol = idMeta.getColumns().get(0);
            setFieldValue(idCol.getField(), entity, value);
        }
    }

    private void setFieldValue(Field field, Object target, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field " + field.getName(), e);
        }
    }

    private Object coerceIdType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (value instanceof Number) {
            Number num = (Number) value;
            if (targetType == int.class || targetType == Integer.class) return num.intValue();
            if (targetType == long.class || targetType == Long.class) return num.longValue();
        }
        return value;
    }

    @Override
    public SelectQuery<T> select() {
        return new SelectQuery<>(metadata, dialect, connectionProvider, metadataRegistry);
    }

    public EntityMetadata getMetadata() {
        return metadata;
    }

    public ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    public Dialect getDialect() {
        return dialect;
    }

    private static final class InsertJoinColumnBinding {
        private final String columnName;
        private final Object value;

        private InsertJoinColumnBinding(String columnName, Object value) {
            this.columnName = columnName;
            this.value = value;
        }

        private String getColumnName() {
            return columnName;
        }

        private Object getValue() {
            return value;
        }
    }
}
