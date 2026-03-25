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

import lombok.EqualsAndHashCode;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.pagination.Page;
import tech.guilhermekaua.spigotboot.core.pagination.Pageable;
import tech.guilhermekaua.spigotboot.core.pagination.Sort;
import tech.guilhermekaua.spigotboot.data.converter.AttributeConverter;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Include;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Param;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Query;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.*;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.mapper.EntityRowMapper;

import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class QueryMethodHandler {
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
        String sql = trimTrailingSemicolon(parsedQueryTemplate.getSql());
        boolean selectQuery = isSelectQuery(sql);
        Class<?> returnType = method.getReturnType();
        Set<String> rootIncludeColumns = collectRootManyToOneColumns(method, entityMetadata);

        if (isPageReturn(returnType)) {
            if (!selectQuery) {
                throw new IllegalStateException(
                        "@Query method " + method.getName() + " must use SELECT when returning Page"
                );
            }

            Pageable pageable = resolveRequiredPageable(method, args);
            return executePagedEntityQuery(
                    method,
                    sql,
                    positionalParams,
                    entityMetadata,
                    dialect,
                    rootIncludeColumns,
                    pageable
            );
        }

        if (isEntityClassReturn(method, entityMetadata.getEntityClass())) {
            if (!selectQuery) {
                throw new IllegalStateException(
                        "@Query method " + method.getName() + " must use SELECT when returning entities"
                );
            }

            EntityQueryResult queryResult = executeEntityQuery(sql, positionalParams, entityMetadata, rootIncludeColumns);
            applyIncludes(method, queryResult.getEntities(), entityMetadata, dialect, queryResult.getRootColumnValues());
            return adaptEntityReturn(queryResult.getEntities(), returnType);
        }

        if (isVoidReturn(returnType)) {
            if (selectQuery) {
                executeScalarQuery(sql, positionalParams);
            } else {
                executeUpdate(sql, positionalParams);
            }

            return null;
        }

        Object scalar = selectQuery
                ? executeScalarQuery(sql, positionalParams)
                : Integer.valueOf(executeUpdate(sql, positionalParams));
        return adaptScalarReturn(method, scalar);
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

    private Page<Object> executePagedEntityQuery(
            Method method,
            String sql,
            List<Object> positionalParams,
            EntityMetadata entityMetadata,
            Dialect dialect,
            Set<String> rootIncludeColumns,
            Pageable pageable
    ) {
        if (hasTopLevelLimitOrOffset(sql)) {
            throw new IllegalStateException(
                    "@Query method " + method.getName() +
                            " already declares LIMIT/OFFSET. Remove SQL pagination and use Pageable only."
            );
        }

        String countSourceSql = stripTopLevelOrderBy(sql);
        String countSql = "SELECT COUNT(*) FROM (" + countSourceSql + ") AS __spigot_boot_count__";
        long totalElements = executeCountQuery(countSql, positionalParams);

        String sortedSql = appendPageableSort(sql, pageable, entityMetadata, dialect);
        long requestedOffset = pageable.getOffset();
        if (requestedOffset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Pageable offset exceeds supported range for JDBC pagination");
        }

        String pagedSql = dialect.paginationSql(sortedSql, pageable.getPageSize(), (int) requestedOffset);
        EntityQueryResult queryResult = executeEntityQuery(pagedSql, positionalParams, entityMetadata, rootIncludeColumns);
        applyIncludes(method, queryResult.getEntities(), entityMetadata, dialect, queryResult.getRootColumnValues());
        return new Page<>(queryResult.getEntities(), totalElements, pageable.getPageNumber(), pageable.getPageSize());
    }

    private long executeCountQuery(String countSql, List<Object> positionalParams) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            bindParameters(ps, positionalParams);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }

                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute @Query page count", e);
        }
    }

    private Pageable resolveRequiredPageable(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        int pageableIndex = -1;

        for (int i = 0; i < parameters.length; i++) {
            if (!Pageable.class.isAssignableFrom(parameters[i].getType())) {
                continue;
            }

            if (pageableIndex != -1) {
                throw new IllegalStateException(
                        "@Query method " + method.getName() +
                                " returning Page must declare exactly one Pageable parameter"
                );
            }

            pageableIndex = i;
        }

        if (pageableIndex == -1) {
            throw new IllegalStateException(
                    "@Query method " + method.getName() +
                            " returning Page must declare exactly one Pageable parameter"
            );
        }

        Object[] safeArgs = args == null ? new Object[0] : args;
        Object pageableArg = pageableIndex < safeArgs.length ? safeArgs[pageableIndex] : null;
        if (!(pageableArg instanceof Pageable)) {
            throw new IllegalStateException(
                    "@Query method " + method.getName() +
                            " requires a non-null Pageable argument"
            );
        }

        return (Pageable) pageableArg;
    }

    private String appendPageableSort(String sql, Pageable pageable, EntityMetadata entityMetadata, Dialect dialect) {
        if (!pageable.getSort().isSorted() || hasTopLevelOrderBy(sql)) {
            return sql;
        }

        StringJoiner orderJoiner = new StringJoiner(", ");
        for (Sort.Order order : pageable.getSort().getOrders()) {
            String resolvedColumn = resolveColumnOrProperty(entityMetadata, order.getProperty());
            orderJoiner.add(dialect.quoteIdentifier(resolvedColumn) + (order.getDirection() == Sort.Direction.ASC ? " ASC" : " DESC"));
        }

        return sql + " ORDER BY " + orderJoiner;
    }

    private String stripTopLevelOrderBy(String sql) {
        int orderByIndex = findTopLevelOrderByIndex(sql);
        if (orderByIndex < 0) {
            return sql;
        }

        return sql.substring(0, orderByIndex).trim();
    }

    private boolean hasTopLevelOrderBy(String sql) {
        return findTopLevelOrderByIndex(sql) >= 0;
    }

    private int findTopLevelOrderByIndex(String sql) {
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (current == '\'' || current == '"' || current == '`') {
                i = skipQuotedSection(sql, i, current);
                continue;
            }

            if (current == '(') {
                depth++;
                continue;
            }

            if (current == ')') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }

            if (depth != 0 || !isKeywordAt(sql, i, "order")) {
                continue;
            }

            int cursor = i + "order".length();
            while (cursor < sql.length() && Character.isWhitespace(sql.charAt(cursor))) {
                cursor++;
            }

            if (isKeywordAt(sql, cursor, "by")) {
                return i;
            }
        }

        return -1;
    }

    private boolean hasTopLevelLimitOrOffset(String sql) {
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (current == '\'' || current == '"' || current == '`') {
                i = skipQuotedSection(sql, i, current);
                continue;
            }

            if (current == '(') {
                depth++;
                continue;
            }

            if (current == ')') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }

            if (depth == 0 && (isKeywordAt(sql, i, "limit") || isKeywordAt(sql, i, "offset"))) {
                return true;
            }
        }

        return false;
    }

    private int skipQuotedSection(String text, int startIndex, char quoteChar) {
        int i = startIndex + 1;
        while (i < text.length()) {
            char current = text.charAt(i);
            if (current == quoteChar) {
                if (quoteChar == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }

                return i;
            }

            if (current == '\\' && i + 1 < text.length()) {
                i += 2;
                continue;
            }

            i++;
        }

        return text.length() - 1;
    }

    private boolean isKeywordAt(String text, int index, String keyword) {
        if (index < 0 || index + keyword.length() > text.length()) {
            return false;
        }

        if (!text.regionMatches(true, index, keyword, 0, keyword.length())) {
            return false;
        }

        int previousIndex = index - 1;
        if (previousIndex >= 0 && isIdentifierCharacter(text.charAt(previousIndex))) {
            return false;
        }

        int nextIndex = index + keyword.length();
        return nextIndex >= text.length() || !isIdentifierCharacter(text.charAt(nextIndex));
    }

    private boolean isIdentifierCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }

    private String trimTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private void bindParameters(PreparedStatement ps, List<Object> positionalParams) throws SQLException {
        for (int i = 0; i < positionalParams.size(); i++) {
            ps.setObject(i + 1, positionalParams.get(i));
        }
    }

    private boolean isEntityClassReturn(Method method, Class<?> entityClass) {
        Class<?> returnType = method.getReturnType();
        if (returnType == entityClass) {
            return true;
        }

        if (List.class.isAssignableFrom(returnType) || Optional.class.isAssignableFrom(returnType)) {
            return entityClass == resolveGenericComponent(method.getGenericReturnType());
        }

        return false;
    }

    private Class<?> resolveGenericComponent(Type genericType) {
        if (!(genericType instanceof ParameterizedType)) {
            return null;
        }

        Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
        if (args.length == 0 || !(args[0] instanceof Class)) {
            return null;
        }

        return (Class<?>) args[0];
    }

    private boolean isPageReturn(Class<?> returnType) {
        return Page.class.isAssignableFrom(returnType);
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

    private Object adaptScalarReturn(Method method, Object value) {
        Class<?> returnType = method.getReturnType();

        if (List.class.isAssignableFrom(returnType)) {
            throw new IllegalStateException(
                    "@Query method " + method.getName() + " declares scalar return type " +
                            method.getGenericReturnType().getTypeName() +
                            "; scalar @Query methods return at most one value. " +
                            "Use an entity return type or Optional instead."
            );
        }

        if (Optional.class.isAssignableFrom(returnType)) {
            Class<?> innerType = resolveGenericComponent(method.getGenericReturnType());
            Object converted = innerType != null ? convertScalarValue(innerType, value) : value;
            return Optional.ofNullable(converted);
        }

        return convertScalarValue(returnType, value);
    }

    @SuppressWarnings("unchecked")
    private Object convertScalarValue(Class<?> targetType, Object value) {
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value == null) {
                return false;
            }

            if (value instanceof Boolean) {
                return value;
            }

            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }

            return Boolean.parseBoolean(value.toString());
        }

        if (targetType == int.class || targetType == Integer.class) {
            if (value == null) {
                return 0;
            }

            if (value instanceof Number) {
                return ((Number) value).intValue();
            }

            return Integer.parseInt(value.toString());
        }

        if (targetType == long.class || targetType == Long.class) {
            if (value == null) {
                return 0L;
            }

            if (value instanceof Number) {
                return ((Number) value).longValue();
            }

            return Long.parseLong(value.toString());
        }

        AttributeConverter<?, ?> converter = converterRegistry.getConverter(targetType);
        if (converter != null) {
            return ((AttributeConverter<Object, Object>) converter).convertToEntityAttribute(value);
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
        StringBuilder parsedSql = new StringBuilder(sqlTemplate.length());
        List<String> orderedParamNames = new ArrayList<>();

        for (int i = 0; i < sqlTemplate.length(); i++) {
            char current = sqlTemplate.charAt(i);

            if (current == '\'' || current == '"' || current == '`') {
                int quoteEnd = skipQuotedSection(sqlTemplate, i, current);
                parsedSql.append(sqlTemplate, i, quoteEnd + 1);
                i = quoteEnd;
                continue;
            }

            if (current == '$') {
                int dollarQuoteEnd = skipDollarQuotedSection(sqlTemplate, i);
                if (dollarQuoteEnd > i) {
                    parsedSql.append(sqlTemplate, i, dollarQuoteEnd + 1);
                    i = dollarQuoteEnd;
                    continue;
                }
            }

            if (current == ':' && i + 1 < sqlTemplate.length() && sqlTemplate.charAt(i + 1) == ':') {
                parsedSql.append("::");
                i++;
                continue;
            }

            if (current == ':' && i + 1 < sqlTemplate.length() && isNamedParameterCharacter(sqlTemplate.charAt(i + 1))) {
                int paramEnd = i + 2;
                while (paramEnd < sqlTemplate.length() && isNamedParameterCharacter(sqlTemplate.charAt(paramEnd))) {
                    paramEnd++;
                }

                orderedParamNames.add(sqlTemplate.substring(i + 1, paramEnd));
                parsedSql.append('?');
                i = paramEnd - 1;
                continue;
            }

            parsedSql.append(current);
        }

        return new ParsedQueryTemplate(parsedSql.toString(), orderedParamNames);
    }

    private int skipDollarQuotedSection(String text, int startIndex) {
        int delimiterEnd = findDollarQuoteDelimiterEnd(text, startIndex);
        if (delimiterEnd < 0) {
            return startIndex;
        }

        String delimiter = text.substring(startIndex, delimiterEnd + 1);
        int closingIndex = text.indexOf(delimiter, delimiterEnd + 1);
        if (closingIndex < 0) {
            return text.length() - 1;
        }

        return closingIndex + delimiter.length() - 1;
    }

    private int findDollarQuoteDelimiterEnd(String text, int startIndex) {
        if (startIndex < 0 || startIndex >= text.length() || text.charAt(startIndex) != '$') {
            return -1;
        }

        for (int i = startIndex + 1; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '$') {
                return i;
            }

            if (!isNamedParameterCharacter(current)) {
                return -1;
            }
        }

        return -1;
    }

    private boolean isNamedParameterCharacter(char current) {
        return Character.isLetterOrDigit(current) || current == '_';
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

    private void processIncludes(
            List<?> results,
            Include[] includes,
            EntityMetadata entityMetadata,
            Dialect dialect,
            Map<Object, Map<String, Object>> rootColumnValues
    ) {
        for (Include include : includes) {
            List<String> relationshipPath = parseRelationshipPath(include.value());
            processIncludePath(
                    results,
                    entityMetadata,
                    rootColumnValues,
                    relationshipPath,
                    0,
                    new IncludeQueryOptions(include.where(), include.orderBy()),
                    dialect
            );
        }
    }

    private void processIncludePath(
            List<?> parentResults,
            EntityMetadata parentMeta,
            Map<Object, Map<String, Object>> parentColumnValues,
            List<String> relationshipPath,
            int depth,
            IncludeQueryOptions leafOptions,
            Dialect dialect
    ) {
        if (parentResults.isEmpty()) {
            return;
        }

        String relationshipName = relationshipPath.get(depth);
        RelationshipMetadata relationship = parentMeta.getRelationship(relationshipName);
        EntityMetadata targetMeta = metadataRegistry.getOrParse(relationship.getTargetEntityClass());

        boolean leafPath = depth == relationshipPath.size() - 1;
        IncludeQueryOptions includeOptions = leafPath ? leafOptions : IncludeQueryOptions.empty();

        String nestedRelationshipName = leafPath ? null : relationshipPath.get(depth + 1);
        Set<String> nestedManyToOneColumns = collectUnmappedManyToOneColumns(targetMeta, nestedRelationshipName);

        IncludeProcessingResult includeResult;
        if (relationship.getType() == RelationshipMetadata.RelationshipType.ONE_TO_MANY) {
            includeResult = processOneToManyInclude(
                    parentResults,
                    parentMeta,
                    relationship,
                    targetMeta,
                    includeOptions,
                    parentColumnValues,
                    nestedManyToOneColumns,
                    dialect
            );
        } else {
            includeResult = processManyToOneInclude(
                    parentResults,
                    parentMeta,
                    relationship,
                    targetMeta,
                    includeOptions,
                    dialect,
                    parentColumnValues,
                    nestedManyToOneColumns
            );
        }

        if (!leafPath) {
            processIncludePath(
                    includeResult.getRelatedEntities(),
                    targetMeta,
                    includeResult.getRelatedColumnValues(),
                    relationshipPath,
                    depth + 1,
                    leafOptions,
                    dialect
            );
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private IncludeProcessingResult processOneToManyInclude(
            List<?> parentResults,
            EntityMetadata parentMeta,
            RelationshipMetadata relationship,
            EntityMetadata targetMeta,
            IncludeQueryOptions includeOptions,
            Map<Object, Map<String, Object>> parentColumnValues,
            Set<String> nestedManyToOneColumns,
            Dialect dialect
    ) {
        List<String> childJoinColumns = relationship.getJoinColumns().stream()
                .map(RelationshipJoinColumn::getColumnName)
                .collect(Collectors.toList());
        List<String> parentReferencedColumns = relationship.getJoinColumns().stream()
                .map(RelationshipJoinColumn::getReferencedColumnName)
                .collect(Collectors.toList());

        Map<Object, JoinKey> parentKeyByEntity = new IdentityHashMap<>();
        LinkedHashSet<JoinKey> uniqueParentKeys = new LinkedHashSet<>();
        for (Object parent : parentResults) {
            JoinKey parentKey = resolveEntityJoinKey(parent, parentMeta, parentReferencedColumns, parentColumnValues);
            parentKeyByEntity.put(parent, parentKey);
            if (parentKey != null) {
                uniqueParentKeys.add(parentKey);
            }
        }

        if (uniqueParentKeys.isEmpty()) {
            return IncludeProcessingResult.empty();
        }

        LinkedHashSet<String> capturedColumns = new LinkedHashSet<>(nestedManyToOneColumns);
        capturedColumns.addAll(childJoinColumns);

        List<EntityRowMapper.MappedRow<Object>> secondaryResults = executeIncludeQueryWithCapturedColumns(
                targetMeta,
                childJoinColumns,
                new ArrayList<>(uniqueParentKeys),
                includeOptions,
                dialect,
                capturedColumns
        );

        Map<JoinKey, List<Object>> groupedByParentKey = new HashMap<>();
        List<Object> relatedEntities = new ArrayList<>(secondaryResults.size());
        Map<Object, Map<String, Object>> relatedColumnValues = new IdentityHashMap<>();

        for (EntityRowMapper.MappedRow<Object> mappedRow : secondaryResults) {
            Object childEntity = mappedRow.getEntity();
            Map<String, Object> capturedValues = mappedRow.getColumnValues();
            JoinKey fkValue = resolveCapturedJoinKey(capturedValues, childJoinColumns);
            if (fkValue == null) {
                continue;
            }

            groupedByParentKey.computeIfAbsent(fkValue, key -> new ArrayList<>()).add(childEntity);
            relatedEntities.add(childEntity);

            if (!nestedManyToOneColumns.isEmpty()) {
                Map<String, Object> nestedColumns = new LinkedHashMap<>();
                for (String columnName : nestedManyToOneColumns) {
                    nestedColumns.put(columnName, capturedValues.get(columnName));
                }
                relatedColumnValues.put(childEntity, nestedColumns);
            }
        }

        for (Object parent : parentResults) {
            JoinKey parentKey = parentKeyByEntity.get(parent);
            List<Object> children = parentKey == null
                    ? Collections.emptyList()
                    : groupedByParentKey.getOrDefault(parentKey, Collections.emptyList());
            setCollectionFieldValue(relationship.getField(), parent, children);
        }

        return new IncludeProcessingResult(deduplicateByIdentity(relatedEntities), relatedColumnValues);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private IncludeProcessingResult processManyToOneInclude(
            List<?> parentResults,
            EntityMetadata parentMeta,
            RelationshipMetadata relationship,
            EntityMetadata targetMeta,
            IncludeQueryOptions includeOptions,
            Dialect dialect,
            Map<Object, Map<String, Object>> parentColumnValues,
            Set<String> nestedManyToOneColumns
    ) {
        List<String> parentJoinColumns = relationship.getJoinColumns().stream()
                .map(RelationshipJoinColumn::getColumnName)
                .collect(Collectors.toList());
        List<String> targetReferencedColumns = relationship.getJoinColumns().stream()
                .map(RelationshipJoinColumn::getReferencedColumnName)
                .collect(Collectors.toList());

        Map<Object, JoinKey> parentKeyByEntity = new IdentityHashMap<>();
        LinkedHashSet<JoinKey> uniqueParentKeys = new LinkedHashSet<>();
        for (Object parent : parentResults) {
            JoinKey foreignKey = resolveEntityJoinKey(parent, parentMeta, parentJoinColumns, parentColumnValues);
            parentKeyByEntity.put(parent, foreignKey);
            if (foreignKey != null) {
                uniqueParentKeys.add(foreignKey);
            }
        }

        if (uniqueParentKeys.isEmpty()) {
            return IncludeProcessingResult.empty();
        }

        List<JoinKey> fkValues = new ArrayList<>(uniqueParentKeys);
        Map<JoinKey, Object> targetById = new HashMap<>();
        Map<Object, Map<String, Object>> relatedColumnValues = new IdentityHashMap<>();

        if (nestedManyToOneColumns.isEmpty()) {
            List<Object> targets = executeIncludeQuery(targetMeta, targetReferencedColumns, fkValues, includeOptions, dialect);
            for (Object target : targets) {
                JoinKey id = resolveEntityJoinKey(target, targetMeta, targetReferencedColumns, null);
                if (id != null) {
                    targetById.put(id, target);
                }
            }
        } else {
            List<EntityRowMapper.MappedRow<Object>> mappedTargets = executeIncludeQueryWithCapturedColumns(
                    targetMeta,
                    targetReferencedColumns,
                    fkValues,
                    includeOptions,
                    dialect,
                    nestedManyToOneColumns
            );

            for (EntityRowMapper.MappedRow<Object> mappedTarget : mappedTargets) {
                Object targetEntity = mappedTarget.getEntity();
                JoinKey targetId = resolveEntityJoinKey(targetEntity, targetMeta, targetReferencedColumns, null);
                if (targetId != null) {
                    targetById.put(targetId, targetEntity);
                }

                Map<String, Object> nestedColumns = new LinkedHashMap<>();
                for (String columnName : nestedManyToOneColumns) {
                    nestedColumns.put(columnName, mappedTarget.getColumnValues().get(columnName));
                }
                relatedColumnValues.put(targetEntity, nestedColumns);
            }
        }

        List<Object> relatedTargets = new ArrayList<>();
        IdentityHashMap<Object, Boolean> seenTargets = new IdentityHashMap<>();

        for (Object parent : parentResults) {
            JoinKey fkValue = parentKeyByEntity.get(parent);
            if (fkValue == null) {
                continue;
            }

            Object target = targetById.get(fkValue);
            if (target != null) {
                setFieldValue(relationship.getField(), parent, target);

                if (!seenTargets.containsKey(target)) {
                    seenTargets.put(target, Boolean.TRUE);
                    relatedTargets.add(target);
                }
            }
        }

        return new IncludeProcessingResult(relatedTargets, relatedColumnValues);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> executeIncludeQuery(
            EntityMetadata targetMeta,
            List<String> joinColumns,
            List<JoinKey> inValues,
            IncludeQueryOptions includeOptions,
            Dialect dialect
    ) {
        if (inValues.isEmpty()) {
            return Collections.emptyList();
        }

        int columnsPerKey = Math.max(1, joinColumns.size());
        int maxBindParameters = Math.max(1, dialect.maxBindParameters());
        int maxKeysPerChunk = Math.max(1, maxBindParameters / columnsPerKey);
        List<Object> aggregatedResults = new ArrayList<>();

        for (int start = 0; start < inValues.size(); start += maxKeysPerChunk) {
            int end = Math.min(inValues.size(), start + maxKeysPerChunk);
            List<JoinKey> currentChunk = inValues.subList(start, end);
            aggregatedResults.addAll(executeIncludeQueryChunk(targetMeta, joinColumns, currentChunk, includeOptions, dialect));
        }

        return aggregatedResults;
    }

    private List<EntityRowMapper.MappedRow<Object>> executeIncludeQueryWithCapturedColumns(
            EntityMetadata targetMeta,
            List<String> joinColumns,
            List<JoinKey> inValues,
            IncludeQueryOptions includeOptions,
            Dialect dialect,
            Collection<String> capturedColumns
    ) {
        if (inValues.isEmpty()) {
            return Collections.emptyList();
        }

        int columnsPerKey = Math.max(1, joinColumns.size());
        int maxBindParameters = Math.max(1, dialect.maxBindParameters());
        int maxKeysPerChunk = Math.max(1, maxBindParameters / columnsPerKey);
        List<EntityRowMapper.MappedRow<Object>> aggregatedResults = new ArrayList<>();

        for (int start = 0; start < inValues.size(); start += maxKeysPerChunk) {
            int end = Math.min(inValues.size(), start + maxKeysPerChunk);
            List<JoinKey> currentChunk = inValues.subList(start, end);
            aggregatedResults.addAll(executeIncludeQueryChunkWithCapturedColumns(
                    targetMeta,
                    joinColumns,
                    currentChunk,
                    includeOptions,
                    dialect,
                    capturedColumns
            ));
        }

        return aggregatedResults;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> executeIncludeQueryChunk(
            EntityMetadata targetMeta,
            List<String> joinColumns,
            List<JoinKey> inValues,
            IncludeQueryOptions includeOptions,
            Dialect dialect
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(dialect.quoteIdentifier(targetMeta.getTableName()));
        sql.append(" WHERE ");

        List<Object> params = new ArrayList<>();
        appendJoinKeyPredicate(sql, joinColumns, inValues, params, dialect);

        if (!includeOptions.getWhere().isEmpty()) {
            sql.append(" AND ").append(includeOptions.getWhere());
        }

        if (!includeOptions.getOrderBy().isEmpty()) {
            sql.append(" ORDER BY ").append(includeOptions.getOrderBy());
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
    private List<EntityRowMapper.MappedRow<Object>> executeIncludeQueryChunkWithCapturedColumns(
            EntityMetadata targetMeta,
            List<String> joinColumns,
            List<JoinKey> inValues,
            IncludeQueryOptions includeOptions,
            Dialect dialect,
            Collection<String> capturedColumns
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(dialect.quoteIdentifier(targetMeta.getTableName()));
        sql.append(" WHERE ");

        List<Object> params = new ArrayList<>();
        appendJoinKeyPredicate(sql, joinColumns, inValues, params, dialect);

        if (!includeOptions.getWhere().isEmpty()) {
            sql.append(" AND ").append(includeOptions.getWhere());
        }

        if (!includeOptions.getOrderBy().isEmpty()) {
            sql.append(" ORDER BY ").append(includeOptions.getOrderBy());
        }

        EntityRowMapper targetMapper = new EntityRowMapper(targetMeta);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                return targetMapper.mapRowsWithColumns(rs, capturedColumns);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute include query", e);
        }
    }

    private void appendJoinKeyPredicate(
            StringBuilder sql,
            List<String> joinColumns,
            List<JoinKey> joinValues,
            List<Object> params,
            Dialect dialect
    ) {
        if (joinColumns.size() == 1) {
            String joinColumn = joinColumns.get(0);
            sql.append(dialect.quoteIdentifier(joinColumn)).append(" IN (");

            StringJoiner placeholders = new StringJoiner(", ");
            for (JoinKey joinValue : joinValues) {
                placeholders.add("?");
                params.add(joinValue.getValue(0));
            }
            sql.append(placeholders).append(")");
            return;
        }

        StringJoiner orPredicates = new StringJoiner(" OR ");
        for (JoinKey joinValue : joinValues) {
            StringJoiner andPredicates = new StringJoiner(" AND ", "(", ")");
            for (int i = 0; i < joinColumns.size(); i++) {
                andPredicates.add(dialect.quoteIdentifier(joinColumns.get(i)) + " = ?");
                params.add(joinValue.getValue(i));
            }
            orPredicates.add(andPredicates.toString());
        }

        sql.append("(").append(orPredicates).append(")");
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
                Object owner = field.getDeclaringClass().isInstance(entity)
                        ? entity
                        : resolveEmbeddedIdOwner(entity, meta, field.getDeclaringClass());
                if (owner == null) {
                    return null;
                }

                Object value = field.get(owner);

                if (columnMetadata.getConverter() != null && value != null) {
                    return ((AttributeConverter<Object, Object>) columnMetadata.getConverter())
                            .convertToDatabaseColumn(value);
                }

                return value;
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new RuntimeException("Failed to access field for column " + columnName, e);
            }
        }

        return null;
    }

    private Object resolveEmbeddedIdOwner(Object entity, EntityMetadata meta, Class<?> expectedOwnerType) {
        if (!meta.getIdMetadata().isComposite()) {
            return null;
        }

        Class<?> embeddedKeyClass = meta.getIdMetadata().getEmbeddedKeyClass();
        if (embeddedKeyClass == null || !expectedOwnerType.isAssignableFrom(embeddedKeyClass)) {
            return null;
        }

        for (Field field : getAllFields(meta.getEntityClass())) {
            if (!field.isAnnotationPresent(tech.guilhermekaua.spigotboot.data.jdbc.annotation.EmbeddedId.class)) {
                continue;
            }

            if (field.getType() != embeddedKeyClass) {
                continue;
            }

            try {
                field.setAccessible(true);
                return field.get(entity);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access @EmbeddedId field " + field.getName(), e);
            }
        }

        return null;
    }

    private JoinKey resolveEntityJoinKey(
            Object entity,
            EntityMetadata entityMetadata,
            List<String> columnNames,
            Map<Object, Map<String, Object>> capturedColumnValues
    ) {
        Map<String, Object> capturedColumns = capturedColumnValues == null ? null : capturedColumnValues.get(entity);
        List<Object> values = new ArrayList<>(columnNames.size());

        for (String columnName : columnNames) {
            Object value = getFieldValueByColumnName(entity, entityMetadata, columnName);
            if (value == null && capturedColumns != null) {
                value = capturedColumns.get(columnName);
            }
            values.add(value);
        }

        return toJoinKey(values);
    }

    private JoinKey resolveCapturedJoinKey(Map<String, Object> capturedColumns, List<String> columnNames) {
        if (capturedColumns == null || capturedColumns.isEmpty()) {
            return null;
        }

        List<Object> values = new ArrayList<>(columnNames.size());
        for (String columnName : columnNames) {
            values.add(capturedColumns.get(columnName));
        }

        return toJoinKey(values);
    }

    private JoinKey toJoinKey(List<Object> values) {
        if (values.isEmpty()) {
            return null;
        }

        for (Object value : values) {
            if (value == null) {
                return null;
            }
        }

        return new JoinKey(values);
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
            List<String> relationshipPath = parseRelationshipPath(include.value());
            RelationshipMetadata relationship = entityMetadata.getRelationship(relationshipPath.get(0));
            if (relationship.getType() != RelationshipMetadata.RelationshipType.MANY_TO_ONE) {
                continue;
            }

            for (RelationshipJoinColumn joinColumn : relationship.getJoinColumns()) {
                if (isMappedColumn(entityMetadata, joinColumn.getColumnName())) {
                    continue;
                }

                requiredColumns.add(joinColumn.getColumnName());
            }
        }

        return requiredColumns;
    }

    private Set<String> collectUnmappedManyToOneColumns(EntityMetadata sourceMetadata, String relationshipName) {
        if (relationshipName == null) {
            return Collections.emptySet();
        }

        RelationshipMetadata relationship = sourceMetadata.getRelationship(relationshipName);
        if (relationship.getType() != RelationshipMetadata.RelationshipType.MANY_TO_ONE) {
            return Collections.emptySet();
        }

        Set<String> unmappedColumns = new LinkedHashSet<>();
        for (RelationshipJoinColumn joinColumn : relationship.getJoinColumns()) {
            if (isMappedColumn(sourceMetadata, joinColumn.getColumnName())) {
                continue;
            }

            unmappedColumns.add(joinColumn.getColumnName());
        }

        return unmappedColumns;
    }

    private boolean isMappedColumn(EntityMetadata entityMetadata, String columnName) {
        for (ColumnMetadata columnMetadata : entityMetadata.getColumns()) {
            if (columnMetadata.getColumnName().equals(columnName)) {
                return true;
            }
        }

        return false;
    }

    private String resolveColumnOrProperty(EntityMetadata sourceMetadata, String propertyOrColumn) {
        for (ColumnMetadata columnMetadata : sourceMetadata.getColumns()) {
            if (columnMetadata.getField().getName().equals(propertyOrColumn)) {
                return columnMetadata.getColumnName();
            }
        }

        for (ColumnMetadata columnMetadata : sourceMetadata.getColumns()) {
            if (columnMetadata.getColumnName().equals(propertyOrColumn)) {
                return columnMetadata.getColumnName();
            }
        }

        return propertyOrColumn;
    }

    private List<String> parseRelationshipPath(String relationshipPath) {
        if (relationshipPath == null) {
            throw new IllegalArgumentException("Include relationship path cannot be null");
        }

        String trimmedPath = relationshipPath.trim();
        if (trimmedPath.isEmpty()) {
            throw new IllegalArgumentException("Include relationship path cannot be empty");
        }

        String[] rawSegments = trimmedPath.split("\\.");
        List<String> segments = new ArrayList<>(rawSegments.length);

        for (String rawSegment : rawSegments) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Invalid include relationship path '" + relationshipPath + "'");
            }
            segments.add(segment);
        }

        return segments;
    }

    private List<Object> deduplicateByIdentity(List<Object> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        List<Object> deduplicated = new ArrayList<>();

        for (Object entity : entities) {
            if (!seen.containsKey(entity)) {
                seen.put(entity, Boolean.TRUE);
                deduplicated.add(entity);
            }
        }

        return deduplicated;
    }

    private List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        return fields;
    }

    @EqualsAndHashCode
    private static final class JoinKey {
        private final List<Object> values;

        private JoinKey(List<Object> values) {
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        private Object getValue(int index) {
            return values.get(index);
        }
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

    private static final class IncludeQueryOptions {
        private static final IncludeQueryOptions EMPTY = new IncludeQueryOptions("", "");

        private final String where;
        private final String orderBy;

        private IncludeQueryOptions(String where, String orderBy) {
            this.where = where == null ? "" : where;
            this.orderBy = orderBy == null ? "" : orderBy;
        }

        private static IncludeQueryOptions empty() {
            return EMPTY;
        }

        private String getWhere() {
            return where;
        }

        private String getOrderBy() {
            return orderBy;
        }
    }

    private static final class IncludeProcessingResult {
        private static final IncludeProcessingResult EMPTY =
                new IncludeProcessingResult(Collections.emptyList(), Collections.emptyMap());

        private final List<Object> relatedEntities;
        private final Map<Object, Map<String, Object>> relatedColumnValues;

        private IncludeProcessingResult(List<Object> relatedEntities, Map<Object, Map<String, Object>> relatedColumnValues) {
            this.relatedEntities = relatedEntities;
            this.relatedColumnValues = relatedColumnValues;
        }

        private static IncludeProcessingResult empty() {
            return EMPTY;
        }

        private List<Object> getRelatedEntities() {
            return relatedEntities;
        }

        private Map<Object, Map<String, Object>> getRelatedColumnValues() {
            return relatedColumnValues;
        }
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
