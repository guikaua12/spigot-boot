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
package tech.guilhermekaua.spigotboot.data.jdbc.query;

import tech.guilhermekaua.spigotboot.core.pagination.Page;
import tech.guilhermekaua.spigotboot.core.pagination.Pageable;
import tech.guilhermekaua.spigotboot.core.pagination.Sort;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.ColumnMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.RelationshipMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.repository.mapper.EntityRowMapper;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SelectQuery<T> {
    private final EntityMetadata metadata;
    private final Dialect dialect;
    private final ConnectionProvider connectionProvider;
    private final EntityRowMapper<T> rowMapper;
    private final QuerySqlBuilder sqlBuilder;
    private final EntityMetadataRegistry metadataRegistry;

    private final List<WhereCondition> conditions = new ArrayList<>();
    private final List<OrderByEntry> orderBys = new ArrayList<>();
    private final List<IncludeSpec> includes = new ArrayList<>();
    private final List<ExistsSubquery> existsSubqueries = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    public SelectQuery(EntityMetadata metadata, Dialect dialect, ConnectionProvider connectionProvider) {
        this(metadata, dialect, connectionProvider, null);
    }

    public SelectQuery(EntityMetadata metadata, Dialect dialect, ConnectionProvider connectionProvider, EntityMetadataRegistry metadataRegistry) {
        this.metadata = metadata;
        this.dialect = dialect;
        this.connectionProvider = connectionProvider;
        this.rowMapper = new EntityRowMapper<>(metadata);
        this.sqlBuilder = new QuerySqlBuilder(dialect);
        this.metadataRegistry = metadataRegistry;
    }

    public WhereClause<T> where(String column) {
        return new WhereClause<>(this, resolveColumnOrProperty(metadata, column), WhereCondition.Conjunction.NONE);
    }

    public WhereClause<T> and(String column) {
        return new WhereClause<>(this, resolveColumnOrProperty(metadata, column), WhereCondition.Conjunction.AND);
    }

    public WhereClause<T> or(String column) {
        return new WhereClause<>(this, resolveColumnOrProperty(metadata, column), WhereCondition.Conjunction.OR);
    }

    public OrderByClause<T> orderBy(String column) {
        return new OrderByClause<>(this, resolveColumnOrProperty(metadata, column));
    }

    public SelectQuery<T> limit(int n) {
        this.limit = n;
        return this;
    }

    public SelectQuery<T> offset(int n) {
        this.offset = n;
        return this;
    }

    public SelectQuery<T> include(String relationshipName) {
        includes.add(new IncludeSpec(relationshipName, Collections.emptyList(), Collections.emptyList()));
        return this;
    }

    public SelectQuery<T> include(String relationshipName, Consumer<IncludeQuery> customizer) {
        IncludeQuery includeQuery = new IncludeQuery();
        customizer.accept(includeQuery);
        includes.add(new IncludeSpec(relationshipName, includeQuery.getConditions(), includeQuery.getOrderBys()));
        return this;
    }

    public SelectQuery<T> whereHas(String relationship, Consumer<IncludeQuery> filter) {
        IncludeQuery includeQuery = new IncludeQuery();
        filter.accept(includeQuery);
        existsSubqueries.add(new ExistsSubquery(relationship, false, includeQuery.getConditions()));
        return this;
    }

    public SelectQuery<T> whereDoesntHave(String relationship, Consumer<IncludeQuery> filter) {
        IncludeQuery includeQuery = new IncludeQuery();
        filter.accept(includeQuery);
        existsSubqueries.add(new ExistsSubquery(relationship, true, includeQuery.getConditions()));
        return this;
    }

    public List<T> fetchAll() {
        return fetchAllInternal(orderBys, limit, offset);
    }

    private List<T> fetchAllInternal(List<OrderByEntry> appliedOrderBys, Integer appliedLimit, Integer appliedOffset) {
        String sql = buildSqlWithExists(appliedOrderBys, appliedLimit, appliedOffset);
        List<Object> params = collectAllParameters();
        Set<String> rootIncludeColumns = collectRootManyToOneColumns();

        List<T> results;
        Map<Object, Map<String, Object>> rootColumnValues = Collections.emptyMap();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                if (rootIncludeColumns.isEmpty()) {
                    results = rowMapper.mapRows(rs);
                } else {
                    List<EntityRowMapper.MappedRow<T>> mappedRows = rowMapper.mapRowsWithColumns(rs, rootIncludeColumns);
                    results = new ArrayList<>(mappedRows.size());
                    rootColumnValues = new IdentityHashMap<>();

                    for (EntityRowMapper.MappedRow<T> mappedRow : mappedRows) {
                        T entity = mappedRow.getEntity();
                        results.add(entity);
                        rootColumnValues.put(entity, mappedRow.getColumnValues());
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute select query", e);
        }

        // process includes
        if (!includes.isEmpty() && !results.isEmpty()) {
            processIncludes(results, rootColumnValues);
        }

        return results;
    }

    public T fetchOne() {
        this.limit = 1;
        List<T> results = fetchAll();
        return results.isEmpty() ? null : results.get(0);
    }

    public Optional<T> fetchOptional() {
        return Optional.ofNullable(fetchOne());
    }

    public long fetchCount() {
        String sql = buildCountSqlWithExists();
        List<Object> params = collectAllParameters();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute count query", e);
        }
    }

    public Page<T> fetchPage(Pageable pageable) {
        List<OrderByEntry> pageOrderBys = new ArrayList<>(orderBys);

        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort().getOrders()) {
                pageOrderBys.add(new OrderByEntry(
                        resolveColumnOrProperty(metadata, order.getProperty()),
                        order.getDirection() == Sort.Direction.ASC
                ));
            }
        }

        long totalElements = fetchCount();

        long requestedOffset = pageable.getOffset();
        if (requestedOffset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Pageable offset exceeds supported range for JDBC pagination");
        }

        List<T> content = fetchAllInternal(pageOrderBys, pageable.getPageSize(), (int) requestedOffset);

        return new Page<>(content, totalElements, pageable.getPageNumber(), pageable.getPageSize());
    }

    void addCondition(WhereCondition condition) {
        conditions.add(condition);
    }

    void addOrderBy(OrderByEntry entry) {
        orderBys.add(entry);
    }

    private String buildSqlWithExists(List<OrderByEntry> appliedOrderBys, Integer appliedLimit, Integer appliedOffset) {
        String baseSql = sqlBuilder.buildSelectSql(metadata, conditions, Collections.emptyList(), null, null);
        String sqlWithExists = appendExistsSubqueries(baseSql);
        String sqlWithOrderBy = appendOrderBy(sqlWithExists, appliedOrderBys);

        if (appliedLimit != null || appliedOffset != null) {
            int resolvedLimit = appliedLimit != null ? appliedLimit : Integer.MAX_VALUE;
            int resolvedOffset = appliedOffset != null ? appliedOffset : 0;
            return dialect.paginationSql(sqlWithOrderBy, resolvedLimit, resolvedOffset);
        }

        return sqlWithOrderBy;
    }

    private String buildCountSqlWithExists() {
        String baseSql = sqlBuilder.buildCountSql(metadata, conditions);
        return appendExistsSubqueries(baseSql);
    }

    private String appendExistsSubqueries(String baseSql) {
        if (existsSubqueries.isEmpty()) {
            return baseSql;
        }

        StringBuilder sb = new StringBuilder(baseSql);

        boolean hasWhereClause = !conditions.isEmpty();

        for (ExistsSubquery subquery : existsSubqueries) {
            RelationshipMetadata rel = metadata.getRelationship(subquery.getRelationshipName());
            EntityMetadata targetMeta = getTargetMetadata(rel);

            sb.append(hasWhereClause ? " AND " : " WHERE ");
            hasWhereClause = true;

            if (subquery.isNegated()) {
                sb.append("NOT ");
            }

            sb.append("EXISTS (SELECT 1 FROM ");
            sb.append(dialect.quoteIdentifier(targetMeta.getTableName()));
            sb.append(" WHERE ");

            if (rel.getType() == RelationshipMetadata.RelationshipType.ONE_TO_MANY) {
                // child.fk = parent.id
                sb.append(dialect.quoteIdentifier(targetMeta.getTableName()))
                        .append(".").append(dialect.quoteIdentifier(rel.getForeignKeyColumn()))
                        .append(" = ")
                        .append(dialect.quoteIdentifier(metadata.getTableName()))
                        .append(".").append(dialect.quoteIdentifier(metadata.getIdMetadata().getColumns().get(0).getColumnName()));
            } else {
                // child.id = parent.fk
                sb.append(dialect.quoteIdentifier(targetMeta.getTableName()))
                        .append(".").append(dialect.quoteIdentifier(targetMeta.getIdMetadata().getColumns().get(0).getColumnName()))
                        .append(" = ")
                        .append(dialect.quoteIdentifier(metadata.getTableName()))
                        .append(".").append(dialect.quoteIdentifier(rel.getForeignKeyColumn()));
            }

            // append additional conditions
            for (WhereCondition cond : subquery.getConditions()) {
                String resolvedColumn = resolveColumnOrProperty(targetMeta, cond.getColumn());
                sb.append(" AND ").append(dialect.quoteIdentifier(resolvedColumn));
                if (cond.getOperator().isNoValue()) {
                    sb.append(" ").append(cond.getOperator().getSql());
                } else if (cond.getOperator().isCollection()) {
                    Collection<?> values = (Collection<?>) cond.getValue();
                    if (values == null || values.isEmpty()) {
                        throw new IllegalArgumentException("IN condition requires at least one value");
                    }

                    StringJoiner joiner = new StringJoiner(", ");
                    for (int i = 0; i < values.size(); i++) {
                        joiner.add("?");
                    }

                    sb.append(" ").append(cond.getOperator().getSql())
                            .append(" (").append(joiner).append(")");
                } else {
                    sb.append(" ").append(cond.getOperator().getSql()).append(" ?");
                }
            }

            sb.append(")");
        }

        return sb.toString();
    }

    private String appendOrderBy(String baseSql, List<OrderByEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return baseSql;
        }

        StringBuilder sb = new StringBuilder(baseSql);
        sb.append(" ORDER BY ");
        StringJoiner joiner = new StringJoiner(", ");
        for (OrderByEntry entry : entries) {
            joiner.add(dialect.quoteIdentifier(entry.getColumn()) + (entry.isAscending() ? " ASC" : " DESC"));
        }
        sb.append(joiner);
        return sb.toString();
    }

    private List<Object> collectAllParameters() {
        List<Object> params = sqlBuilder.collectParameters(conditions);

        // add parameters from exists subqueries
        for (ExistsSubquery subquery : existsSubqueries) {
            for (WhereCondition cond : subquery.getConditions()) {
                if (!cond.getOperator().isNoValue()) {
                    if (cond.getOperator().isCollection()) {
                        params.addAll((Collection<?>) cond.getValue());
                    } else {
                        params.add(cond.getValue());
                    }
                }
            }
        }

        return params;
    }

    private void processIncludes(List<?> mainResults, Map<Object, Map<String, Object>> rootColumnValues) {
        for (IncludeSpec includeSpec : includes) {
            List<String> relationshipPath = parseRelationshipPath(includeSpec.getRelationshipName());
            processIncludePath(mainResults, metadata, rootColumnValues, relationshipPath, 0, includeSpec);
        }
    }

    private void processIncludePath(
            List<?> mainResults,
            EntityMetadata parentMeta,
            Map<Object, Map<String, Object>> parentColumnValues,
            List<String> relationshipPath,
            int depth,
            IncludeSpec leafIncludeSpec
    ) {
        if (mainResults.isEmpty()) {
            return;
        }

        String relationshipName = relationshipPath.get(depth);
        RelationshipMetadata relationship = parentMeta.getRelationship(relationshipName);
        EntityMetadata targetMeta = getTargetMetadata(relationship);

        boolean leafPath = depth == relationshipPath.size() - 1;
        IncludeSpec includeSpec = leafPath
                ? leafIncludeSpec
                : new IncludeSpec(relationshipName, Collections.emptyList(), Collections.emptyList());

        String nestedRelationshipName = leafPath ? null : relationshipPath.get(depth + 1);
        Set<String> nestedManyToOneColumns = collectUnmappedManyToOneColumns(targetMeta, nestedRelationshipName);

        IncludeProcessingResult includeResult;
        if (relationship.getType() == RelationshipMetadata.RelationshipType.ONE_TO_MANY) {
            includeResult = processOneToManyInclude(
                    mainResults,
                    parentMeta,
                    relationship,
                    targetMeta,
                    includeSpec,
                    nestedManyToOneColumns
            );
        } else {
            includeResult = processManyToOneInclude(
                    mainResults,
                    parentMeta,
                    relationship,
                    targetMeta,
                    includeSpec,
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
                    leafIncludeSpec
            );
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private IncludeProcessingResult processOneToManyInclude(
            List<?> mainResults,
            EntityMetadata parentMeta,
            RelationshipMetadata relationship,
            EntityMetadata targetMeta,
            IncludeSpec includeSpec,
            Set<String> nestedManyToOneColumns
    ) {
        List<Object> parentIds = mainResults.stream()
                .map(parent -> extractIdValue(parent, parentMeta))
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) {
            return IncludeProcessingResult.empty();
        }

        LinkedHashSet<String> capturedColumns = new LinkedHashSet<>(nestedManyToOneColumns);
        capturedColumns.add(relationship.getForeignKeyColumn());

        List<EntityRowMapper.MappedRow<Object>> secondaryResults = executeSecondaryQueryWithCapturedColumns(
                targetMeta,
                relationship.getForeignKeyColumn(),
                parentIds,
                includeSpec,
                capturedColumns
        );

        Map<Object, List<Object>> grouped = new HashMap<>();
        List<Object> relatedEntities = new ArrayList<>(secondaryResults.size());
        Map<Object, Map<String, Object>> relatedColumnValues = new IdentityHashMap<>();

        for (EntityRowMapper.MappedRow<Object> mappedRow : secondaryResults) {
            Object childEntity = mappedRow.getEntity();
            Map<String, Object> capturedValues = mappedRow.getColumnValues();
            Object foreignKeyValue = capturedValues.get(relationship.getForeignKeyColumn());

            grouped.computeIfAbsent(foreignKeyValue, ignored -> new ArrayList<>()).add(childEntity);
            relatedEntities.add(childEntity);

            if (!nestedManyToOneColumns.isEmpty()) {
                Map<String, Object> nestedColumns = new LinkedHashMap<>();
                for (String columnName : nestedManyToOneColumns) {
                    nestedColumns.put(columnName, capturedValues.get(columnName));
                }
                relatedColumnValues.put(childEntity, nestedColumns);
            }
        }

        for (Object parent : mainResults) {
            Object parentId = extractIdValue(parent, parentMeta);
            List<Object> children = grouped.getOrDefault(parentId, Collections.emptyList());
            setCollectionFieldValue(relationship.getField(), parent, children);
        }

        return new IncludeProcessingResult(deduplicateByIdentity(relatedEntities), relatedColumnValues);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private IncludeProcessingResult processManyToOneInclude(
            List<?> mainResults,
            EntityMetadata parentMeta,
            RelationshipMetadata relationship,
            EntityMetadata targetMeta,
            IncludeSpec includeSpec,
            Map<Object, Map<String, Object>> parentColumnValues,
            Set<String> nestedManyToOneColumns
    ) {
        List<Object> fkValues = mainResults.stream()
                .map(parent -> resolveManyToOneForeignKeyValue(parent, parentMeta, relationship, parentColumnValues))
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.toList());

        if (fkValues.isEmpty()) {
            return IncludeProcessingResult.empty();
        }

        String targetIdColumn = targetMeta.getIdMetadata().getColumns().get(0).getColumnName();
        Map<Object, Object> targetById = new HashMap<>();
        Map<Object, Map<String, Object>> relatedColumnValues = new IdentityHashMap<>();

        if (nestedManyToOneColumns.isEmpty()) {
            List<Object> targets = executeSecondaryQuery(targetMeta, targetIdColumn, fkValues, includeSpec);
            for (Object target : targets) {
                targetById.put(extractIdValue(target, targetMeta), target);
            }
        } else {
            List<EntityRowMapper.MappedRow<Object>> mappedTargets = executeSecondaryQueryWithCapturedColumns(
                    targetMeta,
                    targetIdColumn,
                    fkValues,
                    includeSpec,
                    nestedManyToOneColumns
            );

            for (EntityRowMapper.MappedRow<Object> mappedTarget : mappedTargets) {
                Object targetEntity = mappedTarget.getEntity();
                targetById.put(extractIdValue(targetEntity, targetMeta), targetEntity);

                Map<String, Object> nestedColumns = new LinkedHashMap<>();
                for (String columnName : nestedManyToOneColumns) {
                    nestedColumns.put(columnName, mappedTarget.getColumnValues().get(columnName));
                }
                relatedColumnValues.put(targetEntity, nestedColumns);
            }
        }

        List<Object> relatedTargets = new ArrayList<>();
        IdentityHashMap<Object, Boolean> seenTargets = new IdentityHashMap<>();

        for (Object parent : mainResults) {
            Object fkValue = resolveManyToOneForeignKeyValue(parent, parentMeta, relationship, parentColumnValues);
            if (fkValue == null) {
                continue;
            }

            Object target = targetById.get(fkValue);
            if (target == null) {
                continue;
            }

            setFieldValue(relationship.getField(), parent, target);

            if (!seenTargets.containsKey(target)) {
                seenTargets.put(target, Boolean.TRUE);
                relatedTargets.add(target);
            }
        }

        return new IncludeProcessingResult(relatedTargets, relatedColumnValues);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> executeSecondaryQuery(EntityMetadata targetMeta, String inColumn, List<Object> inValues, IncludeSpec includeSpec) {
        if (inValues.isEmpty()) {
            return Collections.emptyList();
        }

        int maxBindParameters = Math.max(1, dialect.maxBindParameters());
        List<Object> results = new ArrayList<>();

        for (int start = 0; start < inValues.size(); start += maxBindParameters) {
            int end = Math.min(inValues.size(), start + maxBindParameters);
            results.addAll(executeSecondaryQueryChunk(targetMeta, inColumn, inValues.subList(start, end), includeSpec));
        }

        return results;
    }

    private List<EntityRowMapper.MappedRow<Object>> executeSecondaryQueryWithCapturedColumns(
            EntityMetadata targetMeta,
            String inColumn,
            List<Object> inValues,
            IncludeSpec includeSpec,
            Collection<String> capturedColumns
    ) {
        if (inValues.isEmpty()) {
            return Collections.emptyList();
        }

        int maxBindParameters = Math.max(1, dialect.maxBindParameters());
        List<EntityRowMapper.MappedRow<Object>> results = new ArrayList<>();

        for (int start = 0; start < inValues.size(); start += maxBindParameters) {
            int end = Math.min(inValues.size(), start + maxBindParameters);
            results.addAll(executeSecondaryQueryChunkWithCapturedColumns(
                    targetMeta,
                    inColumn,
                    inValues.subList(start, end),
                    includeSpec,
                    capturedColumns
            ));
        }

        return results;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> executeSecondaryQueryChunk(EntityMetadata targetMeta, String inColumn, List<Object> inValues, IncludeSpec includeSpec) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(dialect.quoteIdentifier(targetMeta.getTableName()));
        sql.append(" WHERE ").append(dialect.quoteIdentifier(inColumn)).append(" IN (");
        StringJoiner placeholders = new StringJoiner(", ");
        for (int i = 0; i < inValues.size(); i++) {
            placeholders.add("?");
        }
        sql.append(placeholders).append(")");

        List<Object> params = new ArrayList<>(inValues);

        // extra conditions
        for (WhereCondition cond : includeSpec.getConditions()) {
            String resolvedColumn = resolveColumnOrProperty(targetMeta, cond.getColumn());
            sql.append(" AND ").append(dialect.quoteIdentifier(resolvedColumn));
            if (cond.getOperator().isNoValue()) {
                sql.append(" ").append(cond.getOperator().getSql());
            } else if (cond.getOperator().isCollection()) {
                Collection<?> values = (Collection<?>) cond.getValue();
                StringJoiner joiner = new StringJoiner(", ");
                for (int i = 0; i < values.size(); i++) {
                    joiner.add("?");
                }
                sql.append(" IN (").append(joiner).append(")");
                params.addAll(values);
            } else {
                sql.append(" ").append(cond.getOperator().getSql()).append(" ?");
                params.add(cond.getValue());
            }
        }

        // order by
        if (!includeSpec.getOrderBys().isEmpty()) {
            sql.append(" ORDER BY ");
            StringJoiner orderJoiner = new StringJoiner(", ");
            for (OrderByEntry entry : includeSpec.getOrderBys()) {
                String resolvedColumn = resolveColumnOrProperty(targetMeta, entry.getColumn());
                orderJoiner.add(dialect.quoteIdentifier(resolvedColumn) + (entry.isAscending() ? " ASC" : " DESC"));
            }
            sql.append(orderJoiner);
        }

        EntityRowMapper<?> targetMapper = new EntityRowMapper<>(targetMeta);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                return (List) targetMapper.mapRows(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute secondary query for include", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<EntityRowMapper.MappedRow<Object>> executeSecondaryQueryChunkWithCapturedColumns(
            EntityMetadata targetMeta,
            String inColumn,
            List<Object> inValues,
            IncludeSpec includeSpec,
            Collection<String> capturedColumns
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

        for (WhereCondition cond : includeSpec.getConditions()) {
            String resolvedColumn = resolveColumnOrProperty(targetMeta, cond.getColumn());
            sql.append(" AND ").append(dialect.quoteIdentifier(resolvedColumn));
            if (cond.getOperator().isNoValue()) {
                sql.append(" ").append(cond.getOperator().getSql());
            } else if (cond.getOperator().isCollection()) {
                Collection<?> values = (Collection<?>) cond.getValue();
                StringJoiner joiner = new StringJoiner(", ");
                for (int i = 0; i < values.size(); i++) {
                    joiner.add("?");
                }
                sql.append(" IN (").append(joiner).append(")");
                params.addAll(values);
            } else {
                sql.append(" ").append(cond.getOperator().getSql()).append(" ?");
                params.add(cond.getValue());
            }
        }

        if (!includeSpec.getOrderBys().isEmpty()) {
            sql.append(" ORDER BY ");
            StringJoiner orderJoiner = new StringJoiner(", ");
            for (OrderByEntry entry : includeSpec.getOrderBys()) {
                String resolvedColumn = resolveColumnOrProperty(targetMeta, entry.getColumn());
                orderJoiner.add(dialect.quoteIdentifier(resolvedColumn) + (entry.isAscending() ? " ASC" : " DESC"));
            }
            sql.append(orderJoiner);
        }

        EntityRowMapper<?> targetMapper = new EntityRowMapper<>(targetMeta);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParameters(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                return (List) targetMapper.mapRowsWithColumns(rs, capturedColumns);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute secondary query for include", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object extractIdValue(Object entity, EntityMetadata meta) {
        try {
            if (meta.getIdMetadata().isComposite()) {
                // return embedded key object
                for (Field field : meta.getEntityClass().getDeclaredFields()) {
                    if (field.isAnnotationPresent(tech.guilhermekaua.spigotboot.data.jdbc.annotation.EmbeddedId.class)) {
                        field.setAccessible(true);
                        return field.get(entity);
                    }
                }
            }
            ColumnMetadata idCol = meta.getIdMetadata().getColumns().get(0);
            Field idField = idCol.getField();
            idField.setAccessible(true);
            Object value = idField.get(entity);
            // apply converter to get the database representation
            if (idCol.getConverter() != null && value != null) {
                return ((tech.guilhermekaua.spigotboot.data.converter.AttributeConverter<Object, Object>) idCol.getConverter())
                        .convertToDatabaseColumn(value);
            }
            return value;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to extract ID value", e);
        }
    }

    private Object getFieldValueByColumnName(Object entity, EntityMetadata meta, String columnName) {
        // check regular columns
        for (ColumnMetadata col : meta.getColumns()) {
            if (col.getColumnName().equals(columnName)) {
                try {
                    col.getField().setAccessible(true);
                    Object value = col.getField().get(entity);
                    if (col.getConverter() != null && value != null) {
                        return col.getConverter().convertToDatabaseColumn(value);
                    }
                    return value;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to access field for column " + columnName, e);
                }
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

    private EntityMetadata getTargetMetadata(RelationshipMetadata rel) {
        if (metadataRegistry != null) {
            return metadataRegistry.getOrParse(rel.getTargetEntityClass());
        }
        throw new IllegalStateException(
                "EntityMetadataRegistry is required for relationship operations. " +
                        "Ensure the repository is properly initialized."
        );
    }

    private void bindParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private Set<String> collectRootManyToOneColumns() {
        if (includes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> requiredColumns = new LinkedHashSet<>();
        for (IncludeSpec includeSpec : includes) {
            List<String> relationshipPath = parseRelationshipPath(includeSpec.getRelationshipName());
            RelationshipMetadata relationship = metadata.getRelationship(relationshipPath.get(0));
            if (relationship.getType() != RelationshipMetadata.RelationshipType.MANY_TO_ONE) {
                continue;
            }

            if (isMappedColumn(metadata, relationship.getForeignKeyColumn())) {
                continue;
            }

            requiredColumns.add(relationship.getForeignKeyColumn());
        }

        return requiredColumns;
    }

    private boolean isMappedColumn(EntityMetadata sourceMetadata, String columnName) {
        for (ColumnMetadata columnMetadata : sourceMetadata.getColumns()) {
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
            Map<Object, Map<String, Object>> parentColumnValues
    ) {
        Object mappedColumnValue = getFieldValueByColumnName(entity, parentMeta, relationship.getForeignKeyColumn());
        if (mappedColumnValue != null) {
            return mappedColumnValue;
        }

        if (parentColumnValues == null || parentColumnValues.isEmpty()) {
            return null;
        }

        Map<String, Object> capturedColumns = parentColumnValues.get(entity);
        if (capturedColumns == null) {
            return null;
        }

        return capturedColumns.get(relationship.getForeignKeyColumn());
    }

    private Set<String> collectUnmappedManyToOneColumns(EntityMetadata sourceMetadata, String relationshipName) {
        if (relationshipName == null) {
            return Collections.emptySet();
        }

        RelationshipMetadata relationship = sourceMetadata.getRelationship(relationshipName);
        if (relationship.getType() != RelationshipMetadata.RelationshipType.MANY_TO_ONE) {
            return Collections.emptySet();
        }

        if (isMappedColumn(sourceMetadata, relationship.getForeignKeyColumn())) {
            return Collections.emptySet();
        }

        return Collections.singleton(relationship.getForeignKeyColumn());
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
}
