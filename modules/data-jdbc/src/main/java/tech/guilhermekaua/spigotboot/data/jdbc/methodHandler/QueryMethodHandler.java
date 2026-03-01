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
package tech.guilhermekaua.spigotboot.data.jdbc.methodHandler;

import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.data.converter.AttributeConverter;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Include;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Param;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Query;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.ColumnMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.RelationshipMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.mapper.EntityRowMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class QueryMethodHandler {
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":(\\w+)");

    private final ConnectionProvider connectionProvider;
    private final EntityMetadataRegistry metadataRegistry;
    private final TypeConverterRegistry converterRegistry;
    private final Map<Method, ParsedQueryTemplate> parsedQueryCache = new ConcurrentHashMap<>();

    public QueryMethodHandler(
            ConnectionProvider connectionProvider,
            EntityMetadataRegistry metadataRegistry,
            TypeConverterRegistry converterRegistry
    ) {
        this.connectionProvider = connectionProvider;
        this.metadataRegistry = metadataRegistry;
        this.converterRegistry = converterRegistry;
    }

    public Object execute(Method method, Object[] args, EntityMetadata entityMetadata, Dialect dialect) {
        Query queryAnnotation = method.getAnnotation(Query.class);
        if (queryAnnotation == null) {
            throw new IllegalStateException("Method " + method.getName() + " is missing @Query annotation");
        }

        ParsedQueryTemplate parsedQueryTemplate = parsedQueryCache.computeIfAbsent(
                method,
                key -> parseQueryTemplate(queryAnnotation.value())
        );

        Map<String, Object> paramValues = resolveParamValues(method, args);
        List<Object> positionalParams = toPositionalParams(parsedQueryTemplate, paramValues, method);
        String sql = parsedQueryTemplate.getSql();
        boolean selectQuery = isSelectQuery(sql);
        Class<?> returnType = method.getReturnType();
        Set<String> rootIncludeColumns = collectRootManyToOneColumns(method, entityMetadata);

        if (isEntityReturn(returnType)) {
            if (!selectQuery) {
                throw new IllegalStateException(
                        "@Query method " + method.getName() + " must use SELECT when returning entities"
                );
            }

            EntityQueryResult queryResult = executeEntityQuery(sql, positionalParams, entityMetadata, rootIncludeColumns);
            applyIncludes(method, queryResult.getEntities(), entityMetadata, dialect, queryResult.getRootColumnValues());
            return adaptEntityReturn(queryResult.getEntities(), returnType);
        }

        if (isScalarReturn(returnType)) {
            Object scalar = selectQuery
                    ? executeScalarQuery(sql, positionalParams)
                    : Integer.valueOf(executeUpdate(sql, positionalParams));
            return adaptScalarReturn(returnType, scalar);
        }

        if (isVoidReturn(returnType)) {
            if (selectQuery) {
                executeScalarQuery(sql, positionalParams);
            } else {
                executeUpdate(sql, positionalParams);
            }

            return null;
        }

        if (!selectQuery) {
            throw new IllegalStateException("@Query method " + method.getName() + " must use SELECT for object returns");
        }

        EntityQueryResult queryResult = executeEntityQuery(sql, positionalParams, entityMetadata, rootIncludeColumns);
        applyIncludes(method, queryResult.getEntities(), entityMetadata, dialect, queryResult.getRootColumnValues());
        return queryResult.getEntities().isEmpty() ? null : queryResult.getEntities().get(0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private EntityQueryResult executeEntityQuery(
            String sql,
            List<Object> positionalParams,
            EntityMetadata entityMetadata,
            Set<String> rootColumns
    ) {
        EntityRowMapper rowMapper = new EntityRowMapper(entityMetadata);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, positionalParams);

            try (ResultSet rs = ps.executeQuery()) {
                if (rootColumns.isEmpty()) {
                    List<Object> entities = rowMapper.mapRows(rs);
                    return new EntityQueryResult(entities, Collections.emptyMap());
                }

                List<EntityRowMapper.MappedRow<Object>> mappedRows = rowMapper.mapRowsWithColumns(rs, rootColumns);
                List<Object> entities = new ArrayList<>(mappedRows.size());
                Map<Object, Map<String, Object>> rootColumnValues = new IdentityHashMap<>();

                for (EntityRowMapper.MappedRow<Object> mappedRow : mappedRows) {
                    Object entity = mappedRow.getEntity();
                    entities.add(entity);
                    rootColumnValues.put(entity, mappedRow.getColumnValues());
                }

                return new EntityQueryResult(entities, rootColumnValues);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute @Query entity select", e);
        }
    }

    private Object executeScalarQuery(String sql, List<Object> positionalParams) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, positionalParams);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return rs.getObject(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute @Query scalar select", e);
        }
    }

    private int executeUpdate(String sql, List<Object> positionalParams) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, positionalParams);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute @Query update statement", e);
        }
    }

    private void bindParameters(PreparedStatement ps, List<Object> positionalParams) throws SQLException {
        for (int i = 0; i < positionalParams.size(); i++) {
            ps.setObject(i + 1, positionalParams.get(i));
        }
    }

    private boolean isEntityReturn(Class<?> returnType) {
        return List.class.isAssignableFrom(returnType)
                || Optional.class.isAssignableFrom(returnType)
                || (!isScalarReturn(returnType) && !isVoidReturn(returnType));
    }

    private boolean isScalarReturn(Class<?> returnType) {
        return returnType == int.class
                || returnType == Integer.class
                || returnType == long.class
                || returnType == Long.class;
    }

    private boolean isVoidReturn(Class<?> returnType) {
        return returnType == void.class || returnType == Void.class;
    }

    private boolean isSelectQuery(String sql) {
        String normalized = sql.trim();
        return startsWithIgnoreCase(normalized, "select") || startsWithIgnoreCase(normalized, "with");
    }

    private boolean startsWithIgnoreCase(String text, String prefix) {
        if (text.length() < prefix.length()) {
            return false;
        }

        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private Object adaptEntityReturn(List<?> results, Class<?> returnType) {
        if (List.class.isAssignableFrom(returnType)) {
            return results;
        }

        Object first = results.isEmpty() ? null : results.get(0);

        if (Optional.class.isAssignableFrom(returnType)) {
            return Optional.ofNullable(first);
        }

        return first;
    }

    private Object adaptScalarReturn(Class<?> returnType, Object value) {
        if (returnType == int.class || returnType == Integer.class) {
            if (value == null) {
                return 0;
            }

            if (value instanceof Number) {
                return ((Number) value).intValue();
            }

            return Integer.parseInt(value.toString());
        }

        if (returnType == long.class || returnType == Long.class) {
            if (value == null) {
                return 0L;
            }

            if (value instanceof Number) {
                return ((Number) value).longValue();
            }

            return Long.parseLong(value.toString());
        }

        return value;
    }

    private void applyIncludes(
            Method method,
            List<?> results,
            EntityMetadata entityMetadata,
            Dialect dialect,
            Map<Object, Map<String, Object>> rootColumnValues
    ) {
        Include[] includes = method.getAnnotationsByType(Include.class);
        if (includes.length > 0 && !results.isEmpty()) {
            processIncludes(results, includes, entityMetadata, dialect, rootColumnValues);
        }
    }

    private Map<String, Object> resolveParamValues(Method method, Object[] args) {
        Map<String, Object> paramValues = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        Object[] safeArgs = args == null ? new Object[0] : args;

        for (int i = 0; i < parameters.length; i++) {
            Param param = parameters[i].getAnnotation(Param.class);
            if (param == null) {
                continue;
            }

            Object rawValue = i < safeArgs.length ? safeArgs[i] : null;
            Object convertedValue = convertParameterValue(rawValue, parameters[i].getType());
            paramValues.put(param.value(), convertedValue);
        }

        return paramValues;
    }

    private ParsedQueryTemplate parseQueryTemplate(String sqlTemplate) {
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sqlTemplate);
        StringBuilder parsedSql = new StringBuilder();
        List<String> orderedParamNames = new ArrayList<>();

        while (matcher.find()) {
            orderedParamNames.add(matcher.group(1));
            matcher.appendReplacement(parsedSql, "?");
        }

        matcher.appendTail(parsedSql);
        return new ParsedQueryTemplate(parsedSql.toString(), orderedParamNames);
    }

    private List<Object> toPositionalParams(
            ParsedQueryTemplate parsedQueryTemplate,
            Map<String, Object> paramValues,
            Method method
    ) {
        List<Object> positionalParams = new ArrayList<>(parsedQueryTemplate.getOrderedParamNames().size());

        for (String paramName : parsedQueryTemplate.getOrderedParamNames()) {
            if (!paramValues.containsKey(paramName)) {
                throw new IllegalStateException(
                        "@Query method " + method.getName() + " is missing @Param binding for ':" + paramName + "'"
                );
            }

            positionalParams.add(paramValues.get(paramName));
        }

        return positionalParams;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processIncludes(
            List<?> results,
            Include[] includes,
            EntityMetadata entityMetadata,
            Dialect dialect,
            Map<Object, Map<String, Object>> rootColumnValues
    ) {
        for (Include include : includes) {
            RelationshipMetadata rel = entityMetadata.getRelationship(include.value());
            EntityMetadata targetMeta = metadataRegistry.getOrParse(rel.getTargetEntityClass());

            if (rel.getType() == RelationshipMetadata.RelationshipType.HAS_MANY) {
                processHasManyInclude(results, entityMetadata, rel, targetMeta, include, dialect);
            } else {
                processManyToOneInclude(results, entityMetadata, rel, targetMeta, include, dialect, rootColumnValues);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processHasManyInclude(
            List<?> mainResults,
            EntityMetadata parentMeta,
            RelationshipMetadata rel,
            EntityMetadata targetMeta,
            Include include,
            Dialect dialect
    ) {
        List<Object> parentIds = mainResults.stream()
                .map(entity -> extractIdValue(entity, parentMeta))
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) {
            return;
        }

        List<EntityRowMapper.MappedRow<Object>> secondaryResults = executeIncludeQueryWithCapturedColumn(
                targetMeta,
                rel.getForeignKeyColumn(),
                parentIds,
                include,
                dialect,
                rel.getForeignKeyColumn()
        );

        Map<Object, List<Object>> grouped = new HashMap<>();
        for (EntityRowMapper.MappedRow<Object> mappedRow : secondaryResults) {
            Object fkValue = mappedRow.getColumnValues().get(rel.getForeignKeyColumn());
            grouped.computeIfAbsent(fkValue, key -> new ArrayList<>()).add(mappedRow.getEntity());
        }

        for (Object parent : mainResults) {
            Object parentId = extractIdValue(parent, parentMeta);
            List<Object> children = grouped.getOrDefault(parentId, Collections.emptyList());
            setCollectionFieldValue(rel.getField(), parent, children);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processManyToOneInclude(
            List<?> mainResults,
            EntityMetadata parentMeta,
            RelationshipMetadata rel,
            EntityMetadata targetMeta,
            Include include,
            Dialect dialect,
            Map<Object, Map<String, Object>> rootColumnValues
    ) {
        List<Object> fkValues = mainResults.stream()
                .map(entity -> resolveManyToOneForeignKeyValue(entity, parentMeta, rel, rootColumnValues))
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.toList());

        if (fkValues.isEmpty()) {
            return;
        }

        String targetIdColumn = targetMeta.getIdMetadata().getColumns().get(0).getColumnName();
        List<Object> targets = executeIncludeQuery(targetMeta, targetIdColumn, fkValues, include, dialect);

        Map<Object, Object> targetById = new HashMap<>();
        for (Object target : targets) {
            Object id = extractIdValue(target, targetMeta);
            targetById.put(id, target);
        }

        for (Object parent : mainResults) {
            Object fkValue = resolveManyToOneForeignKeyValue(parent, parentMeta, rel, rootColumnValues);
            if (fkValue == null) {
                continue;
            }

            Object target = targetById.get(fkValue);
            if (target != null) {
                setFieldValue(rel.getField(), parent, target);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> executeIncludeQuery(
            EntityMetadata targetMeta,
            String inColumn,
            List<Object> inValues,
            Include include,
            Dialect dialect
    ) {
        if (inValues.isEmpty()) {
            return Collections.emptyList();
        }

        int maxBindParameters = Math.max(1, dialect.maxBindParameters());
        List<Object> aggregatedResults = new ArrayList<>();

        for (int start = 0; start < inValues.size(); start += maxBindParameters) {
            int end = Math.min(inValues.size(), start + maxBindParameters);
            List<Object> currentChunk = inValues.subList(start, end);
            aggregatedResults.addAll(executeIncludeQueryChunk(targetMeta, inColumn, currentChunk, include, dialect));
        }

        return aggregatedResults;
    }

    private List<EntityRowMapper.MappedRow<Object>> executeIncludeQueryWithCapturedColumn(
            EntityMetadata targetMeta,
            String inColumn,
            List<Object> inValues,
            Include include,
            Dialect dialect,
            String capturedColumn
    ) {
        if (inValues.isEmpty()) {
            return Collections.emptyList();
        }

        int maxBindParameters = Math.max(1, dialect.maxBindParameters());
        List<EntityRowMapper.MappedRow<Object>> aggregatedResults = new ArrayList<>();

        for (int start = 0; start < inValues.size(); start += maxBindParameters) {
            int end = Math.min(inValues.size(), start + maxBindParameters);
            List<Object> currentChunk = inValues.subList(start, end);
            aggregatedResults.addAll(executeIncludeQueryChunkWithCapturedColumn(
                    targetMeta,
                    inColumn,
                    currentChunk,
                    include,
                    dialect,
                    capturedColumn
            ));
        }

        return aggregatedResults;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> executeIncludeQueryChunk(
            EntityMetadata targetMeta,
            String inColumn,
            List<Object> inValues,
            Include include,
            Dialect dialect
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(dialect.quoteIdentifier(targetMeta.getTableName()));
        sql.append(" WHERE ").append(dialect.quoteIdentifier(inColumn)).append(" IN (");

        StringJoiner placeholders = new StringJoiner(", ");
        for (int i = 0; i < inValues.size(); i++) {
            placeholders.add("?");
        }
        sql.append(placeholders).append(")");

        List<Object> params = new ArrayList<>(inValues);

        if (!include.where().isEmpty()) {
            sql.append(" AND ").append(include.where());
        }

        if (!include.orderBy().isEmpty()) {
            sql.append(" ORDER BY ").append(include.orderBy());
        }

        EntityRowMapper targetMapper = new EntityRowMapper(targetMeta);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                return targetMapper.mapRows(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute include query", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<EntityRowMapper.MappedRow<Object>> executeIncludeQueryChunkWithCapturedColumn(
            EntityMetadata targetMeta,
            String inColumn,
            List<Object> inValues,
            Include include,
            Dialect dialect,
            String capturedColumn
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(dialect.quoteIdentifier(targetMeta.getTableName()));
        sql.append(" WHERE ").append(dialect.quoteIdentifier(inColumn)).append(" IN (");

        StringJoiner placeholders = new StringJoiner(", ");
        for (int i = 0; i < inValues.size(); i++) {
            placeholders.add("?");
        }
        sql.append(placeholders).append(")");

        List<Object> params = new ArrayList<>(inValues);

        if (!include.where().isEmpty()) {
            sql.append(" AND ").append(include.where());
        }

        if (!include.orderBy().isEmpty()) {
            sql.append(" ORDER BY ").append(include.orderBy());
        }

        EntityRowMapper targetMapper = new EntityRowMapper(targetMeta);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                return targetMapper.mapRowsWithColumns(rs, Collections.singleton(capturedColumn));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute include query", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object extractIdValue(Object entity, EntityMetadata meta) {
        try {
            ColumnMetadata idColumn = meta.getIdMetadata().getColumns().get(0);
            Field idField = idColumn.getField();
            idField.setAccessible(true);
            Object value = idField.get(entity);

            if (idColumn.getConverter() != null && value != null) {
                return ((AttributeConverter<Object, Object>) idColumn.getConverter()).convertToDatabaseColumn(value);
            }

            return value;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to extract ID value", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object getFieldValueByColumnName(Object entity, EntityMetadata meta, String columnName) {
        for (ColumnMetadata columnMetadata : meta.getColumns()) {
            if (!columnMetadata.getColumnName().equals(columnName)) {
                continue;
            }

            try {
                Field field = columnMetadata.getField();
                field.setAccessible(true);
                Object value = field.get(entity);

                if (columnMetadata.getConverter() != null && value != null) {
                    return ((AttributeConverter<Object, Object>) columnMetadata.getConverter())
                            .convertToDatabaseColumn(value);
                }

                return value;
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access field for column " + columnName, e);
            }
        }

        return null;
    }

    private void setFieldValue(Field field, Object target, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field " + field.getName(), e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setCollectionFieldValue(Field field, Object target, List<Object> values) {
        try {
            field.setAccessible(true);
            Object currentValue = field.get(target);

            if (currentValue instanceof Collection) {
                Collection collection = (Collection) currentValue;
                collection.clear();
                collection.addAll(values);
                return;
            }

            field.set(target, new ArrayList<>(values));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set relationship field " + field.getName(), e);
        }
    }

    private Set<String> collectRootManyToOneColumns(Method method, EntityMetadata entityMetadata) {
        Include[] includes = method.getAnnotationsByType(Include.class);
        if (includes.length == 0) {
            return Collections.emptySet();
        }

        Set<String> requiredColumns = new LinkedHashSet<>();
        for (Include include : includes) {
            RelationshipMetadata relationship = entityMetadata.getRelationship(include.value());
            if (relationship.getType() != RelationshipMetadata.RelationshipType.MANY_TO_ONE) {
                continue;
            }

            if (isMappedColumn(entityMetadata, relationship.getForeignKeyColumn())) {
                continue;
            }

            requiredColumns.add(relationship.getForeignKeyColumn());
        }

        return requiredColumns;
    }

    private boolean isMappedColumn(EntityMetadata entityMetadata, String columnName) {
        for (ColumnMetadata columnMetadata : entityMetadata.getColumns()) {
            if (columnMetadata.getColumnName().equals(columnName)) {
                return true;
            }
        }

        return false;
    }

    private Object resolveManyToOneForeignKeyValue(
            Object entity,
            EntityMetadata parentMeta,
            RelationshipMetadata relationship,
            Map<Object, Map<String, Object>> rootColumnValues
    ) {
        Object mappedColumnValue = getFieldValueByColumnName(entity, parentMeta, relationship.getForeignKeyColumn());
        if (mappedColumnValue != null) {
            return mappedColumnValue;
        }

        if (rootColumnValues == null || rootColumnValues.isEmpty()) {
            return null;
        }

        Map<String, Object> capturedColumns = rootColumnValues.get(entity);
        if (capturedColumns == null) {
            return null;
        }

        return capturedColumns.get(relationship.getForeignKeyColumn());
    }

    @SuppressWarnings("unchecked")
    private Object convertParameterValue(Object value, Class<?> declaredType) {
        if (value == null) {
            return null;
        }

        AttributeConverter<Object, Object> converter = resolveConverter(declaredType, value.getClass());
        if (converter == null) {
            return value;
        }

        return converter.convertToDatabaseColumn(value);
    }

    @SuppressWarnings("unchecked")
    private AttributeConverter<Object, Object> resolveConverter(Class<?> declaredType, Class<?> runtimeType) {
        AttributeConverter<?, ?> converter = converterRegistry.getConverter(declaredType);
        if (converter == null && declaredType.isPrimitive()) {
            converter = converterRegistry.getConverter(toWrapperType(declaredType));
        }

        if (converter == null) {
            converter = converterRegistry.getConverter(runtimeType);
        }

        return (AttributeConverter<Object, Object>) converter;
    }

    private Class<?> toWrapperType(Class<?> primitiveType) {
        if (primitiveType == int.class) return Integer.class;
        if (primitiveType == long.class) return Long.class;
        if (primitiveType == double.class) return Double.class;
        if (primitiveType == float.class) return Float.class;
        if (primitiveType == short.class) return Short.class;
        if (primitiveType == byte.class) return Byte.class;
        if (primitiveType == boolean.class) return Boolean.class;
        if (primitiveType == char.class) return Character.class;
        return primitiveType;
    }

    private static final class ParsedQueryTemplate {
        private final String sql;
        private final List<String> orderedParamNames;

        private ParsedQueryTemplate(String sql, List<String> orderedParamNames) {
            this.sql = sql;
            this.orderedParamNames = Collections.unmodifiableList(new ArrayList<>(orderedParamNames));
        }

        private String getSql() {
            return sql;
        }

        private List<String> getOrderedParamNames() {
            return orderedParamNames;
        }
    }

    private static final class EntityQueryResult {
        private final List<Object> entities;
        private final Map<Object, Map<String, Object>> rootColumnValues;

        private EntityQueryResult(List<Object> entities, Map<Object, Map<String, Object>> rootColumnValues) {
            this.entities = entities;
            this.rootColumnValues = rootColumnValues;
        }

        private List<Object> getEntities() {
            return entities;
        }

        private Map<Object, Map<String, Object>> getRootColumnValues() {
            return rootColumnValues;
        }
    }
}
