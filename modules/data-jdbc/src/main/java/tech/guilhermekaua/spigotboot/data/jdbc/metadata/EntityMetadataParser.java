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
package tech.guilhermekaua.spigotboot.data.jdbc.metadata;

import tech.guilhermekaua.spigotboot.data.converter.AttributeConverter;
import tech.guilhermekaua.spigotboot.data.converter.Convert;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.*;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EntityMetadataParser {
    private final TypeConverterRegistry converterRegistry;

    public EntityMetadataParser(TypeConverterRegistry converterRegistry) {
        this.converterRegistry = converterRegistry;
    }

    public EntityMetadata parse(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        if (table == null) {
            throw new IllegalArgumentException("Entity class " + entityClass.getName() + " is missing @Table annotation.");
        }

        String tableName = table.value();
        List<ColumnMetadata> columns = new ArrayList<>();
        List<ColumnMetadata> idColumns = new ArrayList<>();
        Id idAnnotation = null;
        EmbeddedId embeddedIdAnnotation = null;
        Class<?> embeddedKeyClass = null;
        int idFieldCount = 0;
        int embeddedIdFieldCount = 0;

        for (Field field : getAllFields(entityClass)) {
            Id fieldId = field.getAnnotation(Id.class);
            EmbeddedId fieldEmbeddedId = field.getAnnotation(EmbeddedId.class);

            if (fieldEmbeddedId != null) {
                if (idFieldCount > 0) {
                    throw new IllegalArgumentException("Entity class " + entityClass.getName() + " cannot declare both @Id and @EmbeddedId.");
                }

                embeddedIdFieldCount++;
                if (embeddedIdFieldCount > 1) {
                    throw new IllegalArgumentException("Entity class " + entityClass.getName() + " must declare only one @EmbeddedId field.");
                }

                embeddedIdAnnotation = fieldEmbeddedId;
                embeddedKeyClass = field.getType();
                validateEmbeddedKeyClass(embeddedKeyClass, entityClass);
                List<ColumnMetadata> embeddedColumns = parseEmbeddedIdFields(field.getType());
                idColumns.addAll(embeddedColumns);
                columns.addAll(embeddedColumns);
                continue;
            }

            Column column = field.getAnnotation(Column.class);
            if (fieldId == null && column == null) {
                continue;
            }

            String columnName = resolveColumnName(field, column);
            AttributeConverter<Object, Object> converter = resolveConverter(field);
            boolean isId = fieldId != null;

            if (isId) {
                if (embeddedIdFieldCount > 0) {
                    throw new IllegalArgumentException("Entity class " + entityClass.getName() + " cannot declare both @Id and @EmbeddedId.");
                }

                idFieldCount++;
                if (idFieldCount > 1) {
                    throw new IllegalArgumentException("Entity class " + entityClass.getName() + " must declare a single @Id field.");
                }

                idAnnotation = fieldId;
            }

            field.setAccessible(true);
            ColumnMetadata columnMetadata = new ColumnMetadata(field, columnName, field.getType(), converter, isId);
            columns.add(columnMetadata);

            if (isId) {
                idColumns.add(columnMetadata);
            }
        }

        if (idColumns.isEmpty()) {
            throw new IllegalArgumentException("Entity class " + entityClass.getName() + " has no @Id or @EmbeddedId field.");
        }

        boolean isComposite = embeddedIdAnnotation != null;
        IdMetadata idMetadata = new IdMetadata(
                idAnnotation != null ? idAnnotation.strategy() : null,
                idColumns,
                isComposite,
                embeddedKeyClass
        );

        List<RelationshipMetadata> relationships = parseRelationships(entityClass);

        return new EntityMetadata(tableName, entityClass, columns, idMetadata, relationships);
    }

    private List<RelationshipMetadata> parseRelationships(Class<?> entityClass) {
        List<RelationshipMetadata> relationships = new ArrayList<>();

        for (Field field : getAllFields(entityClass)) {
            HasMany hasMany = field.getAnnotation(HasMany.class);
            ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);

            if (hasMany != null) {
                Class<?> targetEntity = resolveCollectionGenericType(field);
                relationships.add(new RelationshipMetadata(
                        field,
                        RelationshipMetadata.RelationshipType.HAS_MANY,
                        hasMany.foreignKey(),
                        targetEntity,
                        true
                ));
            } else if (manyToOne != null) {
                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                if (joinColumn == null) {
                    throw new IllegalArgumentException(
                            "@ManyToOne field " + field.getName() + " on " + entityClass.getName() + " requires @JoinColumn."
                    );
                }

                validateManyToOneTarget(field.getType(), entityClass, field);

                relationships.add(new RelationshipMetadata(
                        field,
                        RelationshipMetadata.RelationshipType.MANY_TO_ONE,
                        joinColumn.value(),
                        field.getType(),
                        false
                ));
            }
        }

        return relationships;
    }

    private Class<?> resolveCollectionGenericType(Field field) {
        if (field.getGenericType() instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) field.getGenericType();
            if (Collection.class.isAssignableFrom((Class<?>) pt.getRawType())) {
                Type typeArg = pt.getActualTypeArguments()[0];
                if (typeArg instanceof Class<?>) {
                    Class<?> resolvedType = (Class<?>) typeArg;
                    if (resolvedType.isInterface() || Modifier.isAbstract(resolvedType.getModifiers())) {
                        throw new IllegalArgumentException(
                                "@HasMany field " + field.getName() + " must target a concrete entity type."
                        );
                    }
                    return resolvedType;
                }
            }
        }
        throw new IllegalArgumentException(
                "Could not resolve generic type for @HasMany field " + field.getName() +
                        ". Ensure the field uses a parameterized Collection type (e.g. List<Entity>)."
        );
    }

    private List<ColumnMetadata> parseEmbeddedIdFields(Class<?> embeddedKeyClass) {
        List<ColumnMetadata> columns = new ArrayList<>();
        for (Field field : getAllFields(embeddedKeyClass)) {
            Column column = field.getAnnotation(Column.class);
            String columnName = resolveColumnName(field, column);
            AttributeConverter<Object, Object> converter = resolveConverter(field);

            field.setAccessible(true);
            columns.add(new ColumnMetadata(field, columnName, field.getType(), converter, true));
        }
        return columns;
    }

    private String resolveColumnName(Field field, Column column) {
        if (column != null && !column.value().isEmpty()) {
            return column.value();
        }
        return field.getName();
    }

    private void validateEmbeddedKeyClass(Class<?> embeddedKeyClass, Class<?> entityClass) {
        try {
            embeddedKeyClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "@EmbeddedId type " + embeddedKeyClass.getName() + " on " + entityClass.getName() +
                            " must declare an accessible no-arg constructor."
            );
        }
    }

    private void validateManyToOneTarget(Class<?> targetEntityType, Class<?> sourceEntityType, Field relationshipField) {
        int idCount = 0;
        Class<?> current = targetEntityType;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(EmbeddedId.class)) {
                    throw new IllegalArgumentException(
                            "@ManyToOne field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                    " references " + targetEntityType.getName() + " which uses @EmbeddedId. " +
                                    "v1 requires a single @Id target."
                    );
                }

                if (field.isAnnotationPresent(Id.class)) {
                    idCount++;
                }
            }

            current = current.getSuperclass();
        }

        if (idCount != 1) {
            throw new IllegalArgumentException(
                    "@ManyToOne field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                            " requires target " + targetEntityType.getName() + " to declare exactly one @Id field."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private AttributeConverter<Object, Object> resolveConverter(Field field) {
        // priority 1: explicit @Convert on field
        Convert convert = field.getAnnotation(Convert.class);
        if (convert != null) {
            try {
                return (AttributeConverter<Object, Object>) convert.converter().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate converter " + convert.converter().getName(), e);
            }
        }

        // priority 2: auto-apply from registry
        return (AttributeConverter<Object, Object>) converterRegistry.getConverter(field.getType());
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }
}
