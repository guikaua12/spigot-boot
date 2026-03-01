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
        String sql = buildSqlWithExists();
        List<Object> params = collectAllParameters();
        Set<String> rootIncludeColumns = collectRootManyToOneColumns();

        List<T> results;
        Map<T, Map<String, Object>> rootColumnValues = Collections.emptyMap();
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
        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort().getOrders()) {
                orderBys.add(new OrderByEntry(
                        resolveColumnOrProperty(metadata, order.getProperty()),
                        order.getDirection() == Sort.Direction.ASC
                ));
            }
        }

        long totalElements = fetchCount();

        this.limit = pageable.getPageSize();
        this.offset = (int) pageable.getOffset();

        List<T> content = fetchAll();

        return new Page<>(content, totalElements, pageable.getPageNumber(), pageable.getPageSize());
    }

    void addCondition(WhereCondition condition) {
        conditions.add(condition);
    }

    void addOrderBy(OrderByEntry entry) {
        orderBys.add(entry);
    }

    private String buildSqlWithExists() {
        String baseSql = sqlBuilder.buildSelectSql(metadata, conditions, orderBys, limit, offset);
        return appendExistsSubqueries(baseSql);
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

            if (rel.getType() == RelationshipMetadata.RelationshipType.HAS_MANY) {
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
                } else {
                    sb.append(" ").append(cond.getOperator().getSql()).append(" ?");
                }
            }

            sb.append(")");
        }

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

    @SuppressWarnings("unchecked")
    private void processIncludes(List<T> mainResults, Map<T, Map<String, Object>> rootColumnValues) {
        for (IncludeSpec includeSpec : includes) {
            RelationshipMetadata rel = metadata.getRelationship(includeSpec.getRelationshipName());
            EntityMetadata targetMeta = getTargetMetadata(rel);

            if (rel.getType() == RelationshipMetadata.RelationshipType.HAS_MANY) {
                processHasManyInclude(mainResults, rel, targetMeta, includeSpec);
            } else {
                processManyToOneInclude(mainResults, rel, targetMeta, includeSpec, rootColumnValues);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processHasManyInclude(List<T> mainResults, RelationshipMetadata rel, EntityMetadata targetMeta, IncludeSpec includeSpec) {
        // collect parent IDs
        List<Object> parentIds = mainResults.stream()
                .map(entity -> extractIdValue(entity, metadata))
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) return;

        // query children: SELECT * FROM child WHERE fk IN (?, ?, ...) [AND extra conditions] [ORDER BY ...]
        List<EntityRowMapper.MappedRow<Object>> secondaryResults = executeSecondaryQueryWithCapturedColumn(
                targetMeta,
                rel.getForeignKeyColumn(),
                parentIds,
                includeSpec,
                rel.getForeignKeyColumn()
        );

        // group by foreign key
        Map<Object, List<Object>> grouped = new HashMap<>();
        for (EntityRowMapper.MappedRow<Object> mappedRow : secondaryResults) {
            Object fkValue = mappedRow.getColumnValues().get(rel.getForeignKeyColumn());
            grouped.computeIfAbsent(fkValue, k -> new ArrayList<>()).add(mappedRow.getEntity());
        }

        // stitch
        for (T parent : mainResults) {
            Object parentId = extractIdValue(parent, metadata);
            List<Object> children = grouped.getOrDefault(parentId, Collections.emptyList());
            setCollectionFieldValue(rel.getField(), parent, children);
        }
    }

    @SuppressWarnings("unchecked")
    private void processManyToOneInclude(
            List<T> mainResults,
            RelationshipMetadata rel,
            EntityMetadata targetMeta,
            IncludeSpec includeSpec,
            Map<T, Map<String, Object>> rootColumnValues
    ) {
        // collect FK values from main results
        List<Object> fkValues = mainResults.stream()
                .map(entity -> resolveManyToOneForeignKeyValue(entity, rel, rootColumnValues))
                .filter(v -> v != null)
                .distinct()
                .collect(Collectors.toList());

        if (fkValues.isEmpty()) return;

        // query targets: SELECT * FROM target WHERE id IN (?, ?, ...)
        String targetIdColumn = targetMeta.getIdMetadata().getColumns().get(0).getColumnName();
        List<Object> targets = executeSecondaryQuery(
                targetMeta, targetIdColumn, fkValues, includeSpec
        );

        // index by ID
        Map<Object, Object> targetById = new HashMap<>();
        for (Object target : targets) {
            Object id = extractIdValue(target, targetMeta);
            targetById.put(id, target);
        }

        // stitch
        for (T parent : mainResults) {
            Object fkValue = resolveManyToOneForeignKeyValue(parent, rel, rootColumnValues);
            if (fkValue != null) {
                Object target = targetById.get(fkValue);
                if (target != null) {
                    setFieldValue(rel.getField(), parent, target);
                }
            }
        }
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

    private List<EntityRowMapper.MappedRow<Object>> executeSecondaryQueryWithCapturedColumn(
            EntityMetadata targetMeta,
            String inColumn,
            List<Object> inValues,
            IncludeSpec includeSpec,
            String capturedColumn
    ) {
        if (inValues.isEmpty()) {
            return Collections.emptyList();
        }

        int maxBindParameters = Math.max(1, dialect.maxBindParameters());
        List<EntityRowMapper.MappedRow<Object>> results = new ArrayList<>();

        for (int start = 0; start < inValues.size(); start += maxBindParameters) {
            int end = Math.min(inValues.size(), start + maxBindParameters);
            results.addAll(executeSecondaryQueryChunkWithCapturedColumn(
                    targetMeta,
                    inColumn,
                    inValues.subList(start, end),
                    includeSpec,
                    capturedColumn
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
    private List<EntityRowMapper.MappedRow<Object>> executeSecondaryQueryChunkWithCapturedColumn(
            EntityMetadata targetMeta,
            String inColumn,
            List<Object> inValues,
            IncludeSpec includeSpec,
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
                return (List) targetMapper.mapRowsWithColumns(rs, Collections.singleton(capturedColumn));
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
            RelationshipMetadata relationship = metadata.getRelationship(includeSpec.getRelationshipName());
            if (relationship.getType() != RelationshipMetadata.RelationshipType.MANY_TO_ONE) {
                continue;
            }

            if (isMappedColumn(relationship.getForeignKeyColumn())) {
                continue;
            }

            requiredColumns.add(relationship.getForeignKeyColumn());
        }

        return requiredColumns;
    }

    private boolean isMappedColumn(String columnName) {
        for (ColumnMetadata columnMetadata : metadata.getColumns()) {
            if (columnMetadata.getColumnName().equals(columnName)) {
                return true;
            }
        }

        return false;
    }

    private Object resolveManyToOneForeignKeyValue(
            T entity,
            RelationshipMetadata relationship,
            Map<T, Map<String, Object>> rootColumnValues
    ) {
        Object mappedColumnValue = getFieldValueByColumnName(entity, metadata, relationship.getForeignKeyColumn());
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
