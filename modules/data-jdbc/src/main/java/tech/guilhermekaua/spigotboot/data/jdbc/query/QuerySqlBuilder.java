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

import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

public class QuerySqlBuilder {
    private final Dialect dialect;

    public QuerySqlBuilder(Dialect dialect) {
        this.dialect = dialect;
    }

    public String buildSelectSql(EntityMetadata metadata, List<WhereCondition> conditions, List<OrderByEntry> orderBys, Integer limit, Integer offset) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(dialect.quoteIdentifier(metadata.getTableName()));

        appendWhereClause(sb, conditions);
        appendOrderBy(sb, orderBys);

        if (limit != null || offset != null) {
            int resolvedLimit = limit != null ? limit : Integer.MAX_VALUE;
            int resolvedOffset = offset != null ? offset : 0;
            return dialect.paginationSql(sb.toString(), resolvedLimit, resolvedOffset);
        }

        return sb.toString();
    }

    public String buildCountSql(EntityMetadata metadata, List<WhereCondition> conditions) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT COUNT(*) FROM ").append(dialect.quoteIdentifier(metadata.getTableName()));
        appendWhereClause(sb, conditions);
        return sb.toString();
    }

    private void appendWhereClause(StringBuilder sb, List<WhereCondition> conditions) {
        if (conditions.isEmpty()) return;

        sb.append(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            WhereCondition cond = conditions.get(i);

            if (i > 0) {
                sb.append(" ").append(cond.getConjunction().name()).append(" ");
            }

            sb.append(dialect.quoteIdentifier(cond.getColumn()));

            if (cond.getOperator().isNoValue()) {
                sb.append(" ").append(cond.getOperator().getSql());
            } else if (cond.getOperator().isCollection()) {
                Collection<?> values = (Collection<?>) cond.getValue();
                StringJoiner joiner = new StringJoiner(", ");
                for (int j = 0; j < values.size(); j++) {
                    joiner.add("?");
                }
                sb.append(" IN (").append(joiner).append(")");
            } else {
                sb.append(" ").append(cond.getOperator().getSql()).append(" ?");
            }
        }
    }

    private void appendOrderBy(StringBuilder sb, List<OrderByEntry> orderBys) {
        if (orderBys.isEmpty()) return;

        sb.append(" ORDER BY ");
        StringJoiner joiner = new StringJoiner(", ");
        for (OrderByEntry entry : orderBys) {
            joiner.add(dialect.quoteIdentifier(entry.getColumn()) + (entry.isAscending() ? " ASC" : " DESC"));
        }
        sb.append(joiner);
    }

    public List<Object> collectParameters(List<WhereCondition> conditions) {
        List<Object> params = new ArrayList<>();
        for (WhereCondition cond : conditions) {
            if (cond.getOperator().isNoValue()) {
                continue;
            }
            if (cond.getOperator().isCollection()) {
                params.addAll((Collection<?>) cond.getValue());
            } else {
                params.add(cond.getValue());
            }
        }
        return params;
    }
}
