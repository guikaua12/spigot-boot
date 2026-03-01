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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class EntityParameterBinder {
    private final EntityMetadata metadata;

    public EntityParameterBinder(EntityMetadata metadata) {
        this.metadata = metadata;
    }

    public int bindInsertParameters(PreparedStatement ps, Object entity, boolean skipIdentityId) throws SQLException {
        int index = 1;
        for (ColumnMetadata col : metadata.getColumns()) {
            if (skipIdentityId && col.isId()) {
                continue;
            }
            if (metadata.getIdMetadata().isComposite() && col.isId()) {
                Object embeddedKey = getEmbeddedKeyValue(entity);
                Object value = getFieldValue(col.getField(), embeddedKey);
                ps.setObject(index++, convertForDb(col, value));
            } else {
                Object value = getFieldValue(col.getField(), entity);
                ps.setObject(index++, convertForDb(col, value));
            }
        }
        return index;
    }

    public int bindInsertParameters(PreparedStatement ps, Object entity) throws SQLException {
        return bindInsertParameters(ps, entity, false);
    }

    public int bindUpdateParameters(PreparedStatement ps, Object entity) throws SQLException {
        int index = 1;

        // set non-id columns first
        for (ColumnMetadata col : metadata.getNonIdColumns()) {
            Object value = getFieldValue(col.getField(), entity);
            ps.setObject(index++, convertForDb(col, value));
        }

        // then id columns in WHERE clause
        index = bindIdParameters(ps, entity, index);

        return index;
    }

    public int bindIdParameters(PreparedStatement ps, Object id, int startIndex) throws SQLException {
        IdMetadata idMeta = metadata.getIdMetadata();
        int index = startIndex;

        if (idMeta.isComposite()) {
            for (ColumnMetadata col : idMeta.getColumns()) {
                Object value = getFieldValue(col.getField(), id);
                ps.setObject(index++, convertForDb(col, value));
            }
        } else {
            ColumnMetadata idCol = idMeta.getColumns().get(0);
            Object value;
            if (isEntityInstance(id)) {
                value = getFieldValue(idCol.getField(), id);
            } else {
                value = id;
            }
            ps.setObject(index++, convertForDb(idCol, value));
        }

        return index;
    }

    public Object extractId(Object entity) {
        IdMetadata idMeta = metadata.getIdMetadata();
        if (idMeta.isComposite()) {
            return getEmbeddedKeyValue(entity);
        }
        return getFieldValue(idMeta.getColumns().get(0).getField(), entity);
    }

    @SuppressWarnings("unchecked")
    private Object convertForDb(ColumnMetadata col, Object value) {
        if (value == null) return null;
        AttributeConverter<Object, Object> converter = col.getConverter();
        if (converter != null) {
            return converter.convertToDatabaseColumn(value);
        }
        return value;
    }

    private Object getFieldValue(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access field " + field.getName(), e);
        }
    }

    private Object getEmbeddedKeyValue(Object entity) {
        IdMetadata idMeta = metadata.getIdMetadata();
        Class<?> embeddedKeyClass = idMeta.getEmbeddedKeyClass();
        for (Field field : metadata.getEntityClass().getDeclaredFields()) {
            if (field.getType() == embeddedKeyClass &&
                    field.isAnnotationPresent(tech.guilhermekaua.spigotboot.data.jdbc.annotation.EmbeddedId.class)) {
                return getFieldValue(field, entity);
            }
        }
        throw new IllegalStateException("No @EmbeddedId field found on " + metadata.getEntityClass().getName());
    }

    private boolean isEntityInstance(Object obj) {
        return metadata.getEntityClass().isInstance(obj);
    }

    public List<ColumnMetadata> getIdColumns() {
        return metadata.getIdMetadata().getColumns();
    }
}
