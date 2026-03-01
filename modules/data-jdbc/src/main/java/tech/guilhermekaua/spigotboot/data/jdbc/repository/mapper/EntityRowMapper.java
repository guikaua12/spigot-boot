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
package tech.guilhermekaua.spigotboot.data.jdbc.repository.mapper;

import tech.guilhermekaua.spigotboot.data.converter.AttributeConverter;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.ColumnMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.IdMetadata;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class EntityRowMapper<T> {
    private final EntityMetadata metadata;

    public EntityRowMapper(EntityMetadata metadata) {
        this.metadata = metadata;
    }

    @SuppressWarnings("unchecked")
    public T mapRow(ResultSet rs) throws SQLException {
        try {
            T entity = (T) metadata.getEntityClass().getDeclaredConstructor().newInstance();

            IdMetadata idMeta = metadata.getIdMetadata();

            if (idMeta.isComposite()) {
                Object embeddedKey = idMeta.getEmbeddedKeyClass().getDeclaredConstructor().newInstance();
                for (ColumnMetadata col : idMeta.getColumns()) {
                    Object value = getValueFromResultSet(rs, col);
                    col.getField().setAccessible(true);
                    col.getField().set(embeddedKey, value);
                }
                // find the embedded id field on the entity and set it
                Field embeddedIdField = findEmbeddedIdField(metadata.getEntityClass(), idMeta.getEmbeddedKeyClass());
                if (embeddedIdField != null) {
                    embeddedIdField.setAccessible(true);
                    embeddedIdField.set(entity, embeddedKey);
                }
            } else {
                for (ColumnMetadata col : idMeta.getColumns()) {
                    Object value = getValueFromResultSet(rs, col);
                    col.getField().setAccessible(true);
                    col.getField().set(entity, value);
                }
            }

            for (ColumnMetadata col : metadata.getNonIdColumns()) {
                Object value = getValueFromResultSet(rs, col);
                col.getField().setAccessible(true);
                col.getField().set(entity, value);
            }

            return entity;
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to map ResultSet row to " + metadata.getEntityClass().getName(), e);
        }
    }

    public List<T> mapRows(ResultSet rs) throws SQLException {
        List<T> results = new ArrayList<>();
        while (rs.next()) {
            results.add(mapRow(rs));
        }
        return results;
    }

    public List<MappedRow<T>> mapRowsWithColumns(ResultSet rs, Collection<String> columnsToCapture) throws SQLException {
        List<MappedRow<T>> results = new ArrayList<>();
        while (rs.next()) {
            T entity = mapRow(rs);

            Map<String, Object> capturedColumns;
            if (columnsToCapture == null || columnsToCapture.isEmpty()) {
                capturedColumns = Collections.emptyMap();
            } else {
                capturedColumns = new LinkedHashMap<>();
                for (String column : columnsToCapture) {
                    capturedColumns.put(column, rs.getObject(column));
                }
            }

            results.add(new MappedRow<>(entity, capturedColumns));
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private Object getValueFromResultSet(ResultSet rs, ColumnMetadata col) throws SQLException {
        Object dbValue = rs.getObject(col.getColumnName());
        if (dbValue == null) {
            return null;
        }

        AttributeConverter<Object, Object> converter = col.getConverter();
        if (converter != null) {
            return converter.convertToEntityAttribute(dbValue);
        }

        // handle primitive type mismatches from JDBC
        return coerceType(dbValue, col.getJavaType());
    }

    private Object coerceType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        // JDBC may return Number types that don't match exactly
        if (value instanceof Number) {
            Number num = (Number) value;
            if (targetType == int.class || targetType == Integer.class) return num.intValue();
            if (targetType == long.class || targetType == Long.class) return num.longValue();
            if (targetType == double.class || targetType == Double.class) return num.doubleValue();
            if (targetType == float.class || targetType == Float.class) return num.floatValue();
            if (targetType == short.class || targetType == Short.class) return num.shortValue();
            if (targetType == byte.class || targetType == Byte.class) return num.byteValue();
            if (targetType == boolean.class || targetType == Boolean.class) return num.intValue() != 0;
        }

        return value;
    }

    private Field findEmbeddedIdField(Class<?> entityClass, Class<?> embeddedKeyClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.getType() == embeddedKeyClass &&
                    field.isAnnotationPresent(tech.guilhermekaua.spigotboot.data.jdbc.annotation.EmbeddedId.class)) {
                return field;
            }
        }
        return null;
    }

    public static final class MappedRow<E> {
        private final E entity;
        private final Map<String, Object> columnValues;

        public MappedRow(E entity, Map<String, Object> columnValues) {
            this.entity = entity;
            this.columnValues = columnValues;
        }

        public E getEntity() {
            return entity;
        }

        public Map<String, Object> getColumnValues() {
            return columnValues;
        }
    }
}
