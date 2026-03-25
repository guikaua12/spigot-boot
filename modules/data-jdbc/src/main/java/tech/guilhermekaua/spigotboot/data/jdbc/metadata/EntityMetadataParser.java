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
import java.util.*;

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

        List<RelationshipMetadata> relationships = parseRelationships(entityClass, idColumns);

        return new EntityMetadata(tableName, entityClass, columns, idMetadata, relationships);
    }

    private List<RelationshipMetadata> parseRelationships(Class<?> entityClass, List<ColumnMetadata> sourceIdColumns) {
        List<RelationshipMetadata> relationships = new ArrayList<>();

        for (Field field : getAllFields(entityClass)) {
            OneToMany oneToMany = field.getAnnotation(OneToMany.class);
            ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);

            if (oneToMany != null) {
                Class<?> targetEntity = resolveCollectionGenericType(field);
                List<RelationshipJoinColumn> joinColumns = resolveOneToManyJoinColumns(
                        entityClass,
                        field,
                        sourceIdColumns
                );
                relationships.add(new RelationshipMetadata(
                        field,
                        RelationshipMetadata.RelationshipType.ONE_TO_MANY,
                        joinColumns,
                        targetEntity,
                        true
                ));
            } else if (manyToOne != null) {
                List<RelationshipJoinColumn> joinColumns = resolveManyToOneJoinColumns(
                        entityClass,
                        field,
                        field.getType()
                );

                relationships.add(new RelationshipMetadata(
                        field,
                        RelationshipMetadata.RelationshipType.MANY_TO_ONE,
                        joinColumns,
                        field.getType(),
                        false
                ));
            }
        }

        return relationships;
    }

    private List<RelationshipJoinColumn> resolveOneToManyJoinColumns(
            Class<?> sourceEntityType,
            Field relationshipField,
            List<ColumnMetadata> sourceIdColumns
    ) {
        List<RawJoinColumnMapping> rawMappings = parseJoinColumnMappings(sourceEntityType, relationshipField, "@OneToMany");
        Set<String> sourceIdColumnNames = new HashSet<>();
        for (ColumnMetadata sourceIdColumn : sourceIdColumns) {
            sourceIdColumnNames.add(sourceIdColumn.getColumnName());
        }

        if (sourceIdColumnNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "@OneToMany field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                            " cannot resolve source id columns"
            );
        }

        if (sourceIdColumnNames.size() == 1) {
            if (rawMappings.size() != 1) {
                throw new IllegalArgumentException(
                        "@OneToMany field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " requires exactly one join mapping for single-column @Id"
                );
            }

            RawJoinColumnMapping mapping = rawMappings.get(0);
            String sourceIdColumnName = sourceIdColumns.get(0).getColumnName();
            String referencedColumn = mapping.referencedColumnName == null
                    ? sourceIdColumnName
                    : mapping.referencedColumnName;

            if (!sourceIdColumnName.equals(referencedColumn)) {
                throw new IllegalArgumentException(
                        "@OneToMany field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " must reference id column '" + sourceIdColumnName + "'"
                );
            }

            return Collections.singletonList(new RelationshipJoinColumn(mapping.columnName, referencedColumn));
        }

        if (rawMappings.size() != sourceIdColumnNames.size()) {
            throw new IllegalArgumentException(
                    "@OneToMany field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                            " requires " + sourceIdColumnNames.size() + " join mappings to match composite @EmbeddedId"
            );
        }

        Set<String> seenReferenced = new HashSet<>();
        List<RelationshipJoinColumn> resolvedMappings = new ArrayList<>(rawMappings.size());

        for (RawJoinColumnMapping mapping : rawMappings) {
            if (mapping.referencedColumnName == null) {
                throw new IllegalArgumentException(
                        "@OneToMany field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " requires referencedColumnName for composite @EmbeddedId mappings"
                );
            }

            if (!sourceIdColumnNames.contains(mapping.referencedColumnName)) {
                throw new IllegalArgumentException(
                        "@OneToMany field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " references unknown source id column '" + mapping.referencedColumnName + "'"
                );
            }

            if (!seenReferenced.add(mapping.referencedColumnName)) {
                throw new IllegalArgumentException(
                        "@OneToMany field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " declares duplicate referencedColumnName '" + mapping.referencedColumnName + "'"
                );
            }

            resolvedMappings.add(new RelationshipJoinColumn(mapping.columnName, mapping.referencedColumnName));
        }

        return resolvedMappings;
    }

    private List<RelationshipJoinColumn> resolveManyToOneJoinColumns(
            Class<?> sourceEntityType,
            Field relationshipField,
            Class<?> targetEntityType
    ) {
        List<RawJoinColumnMapping> rawMappings = parseJoinColumnMappings(sourceEntityType, relationshipField, "@ManyToOne");
        List<ColumnMetadata> targetIdColumns = resolveIdColumns(targetEntityType);

        if (targetIdColumns.size() == 1) {
            if (rawMappings.size() != 1) {
                throw new IllegalArgumentException(
                        "@ManyToOne field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " requires exactly one join mapping for single-column target @Id"
                );
            }

            RawJoinColumnMapping mapping = rawMappings.get(0);
            String targetIdColumnName = targetIdColumns.get(0).getColumnName();
            String referencedColumn = mapping.referencedColumnName == null
                    ? targetIdColumnName
                    : mapping.referencedColumnName;

            if (!targetIdColumnName.equals(referencedColumn)) {
                throw new IllegalArgumentException(
                        "@ManyToOne field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " must reference target id column '" + targetIdColumnName + "'"
                );
            }

            return Collections.singletonList(new RelationshipJoinColumn(mapping.columnName, referencedColumn));
        }

        Set<String> targetIdColumnNames = new HashSet<>();
        for (ColumnMetadata targetIdColumn : targetIdColumns) {
            targetIdColumnNames.add(targetIdColumn.getColumnName());
        }

        if (rawMappings.size() != targetIdColumnNames.size()) {
            throw new IllegalArgumentException(
                    "@ManyToOne field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                            " requires " + targetIdColumnNames.size() + " join mappings to match composite target @EmbeddedId"
            );
        }

        Set<String> seenReferenced = new HashSet<>();
        List<RelationshipJoinColumn> resolvedMappings = new ArrayList<>(rawMappings.size());

        for (RawJoinColumnMapping mapping : rawMappings) {
            if (mapping.referencedColumnName == null) {
                throw new IllegalArgumentException(
                        "@ManyToOne field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " requires referencedColumnName for composite target @EmbeddedId mappings"
                );
            }

            if (!targetIdColumnNames.contains(mapping.referencedColumnName)) {
                throw new IllegalArgumentException(
                        "@ManyToOne field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " references unknown target id column '" + mapping.referencedColumnName + "'"
                );
            }

            if (!seenReferenced.add(mapping.referencedColumnName)) {
                throw new IllegalArgumentException(
                        "@ManyToOne field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " declares duplicate referencedColumnName '" + mapping.referencedColumnName + "'"
                );
            }

            resolvedMappings.add(new RelationshipJoinColumn(mapping.columnName, mapping.referencedColumnName));
        }

        return resolvedMappings;
    }

    private List<RawJoinColumnMapping> parseJoinColumnMappings(
            Class<?> sourceEntityType,
            Field relationshipField,
            String relationshipType
    ) {
        JoinColumn[] joinColumns = relationshipField.getAnnotationsByType(JoinColumn.class);
        if (joinColumns.length == 0) {
            throw new IllegalArgumentException(
                    relationshipType + " field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                            " requires @JoinColumn or @JoinColumns."
            );
        }

        List<RawJoinColumnMapping> mappings = new ArrayList<>(joinColumns.length);
        Set<String> seenColumns = new HashSet<>();

        for (JoinColumn joinColumn : joinColumns) {
            String valueColumnName = trimToNull(joinColumn.value());
            String explicitColumnName = trimToNull(joinColumn.columnName());
            String referencedColumnName = trimToNull(joinColumn.referencedColumnName());

            if (valueColumnName != null && explicitColumnName != null && !valueColumnName.equals(explicitColumnName)) {
                throw new IllegalArgumentException(
                        relationshipType + " field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " declares both JoinColumn.value and JoinColumn.columnName with different values"
                );
            }

            String columnName = explicitColumnName != null ? explicitColumnName : valueColumnName;
            if (columnName == null) {
                throw new IllegalArgumentException(
                        relationshipType + " field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " declares an empty @JoinColumn"
                );
            }

            if (!seenColumns.add(columnName)) {
                throw new IllegalArgumentException(
                        relationshipType + " field " + relationshipField.getName() + " on " + sourceEntityType.getName() +
                                " declares duplicate join column '" + columnName + "'"
                );
            }

            mappings.add(new RawJoinColumnMapping(columnName, referencedColumnName));
        }

        return mappings;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                                "@OneToMany field " + field.getName() + " must target a concrete entity type."
                        );
                    }
                    return resolvedType;
                }
            }
        }
        throw new IllegalArgumentException(
                "Could not resolve generic type for @OneToMany field " + field.getName() +
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

    private List<ColumnMetadata> resolveIdColumns(Class<?> entityClass) {
        List<ColumnMetadata> idColumns = new ArrayList<>();
        int idFieldCount = 0;
        int embeddedIdFieldCount = 0;

        for (Field field : getAllFields(entityClass)) {
            EmbeddedId embeddedId = field.getAnnotation(EmbeddedId.class);
            Id id = field.getAnnotation(Id.class);

            if (embeddedId != null) {
                if (idFieldCount > 0) {
                    throw new IllegalArgumentException(
                            "Entity class " + entityClass.getName() + " cannot declare both @Id and @EmbeddedId."
                    );
                }

                embeddedIdFieldCount++;
                if (embeddedIdFieldCount > 1) {
                    throw new IllegalArgumentException(
                            "Entity class " + entityClass.getName() + " must declare only one @EmbeddedId field."
                    );
                }

                validateEmbeddedKeyClass(field.getType(), entityClass);
                idColumns.addAll(parseEmbeddedIdFields(field.getType()));
                continue;
            }

            if (id == null) {
                continue;
            }

            if (embeddedIdFieldCount > 0) {
                throw new IllegalArgumentException(
                        "Entity class " + entityClass.getName() + " cannot declare both @Id and @EmbeddedId."
                );
            }

            idFieldCount++;
            if (idFieldCount > 1) {
                throw new IllegalArgumentException(
                        "Entity class " + entityClass.getName() + " must declare a single @Id field."
                );
            }

            Column column = field.getAnnotation(Column.class);
            String columnName = resolveColumnName(field, column);
            AttributeConverter<Object, Object> converter = resolveConverter(field);
            field.setAccessible(true);
            idColumns.add(new ColumnMetadata(field, columnName, field.getType(), converter, true));
        }

        if (idColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Entity class " + entityClass.getName() + " has no @Id or @EmbeddedId field."
            );
        }

        return idColumns;
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
                if (Modifier.isStatic(field.getModifiers())
                        || Modifier.isTransient(field.getModifiers())
                        || field.isSynthetic()) {
                    continue;
                }
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static final class RawJoinColumnMapping {
        private final String columnName;
        private final String referencedColumnName;

        private RawJoinColumnMapping(String columnName, String referencedColumnName) {
            this.columnName = columnName;
            this.referencedColumnName = referencedColumnName;
        }
    }
}
