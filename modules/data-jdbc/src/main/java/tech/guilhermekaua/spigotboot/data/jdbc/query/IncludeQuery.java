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

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class IncludeQuery {
    private final List<WhereCondition> conditions = new ArrayList<>();
    private final List<OrderByEntry> orderBys = new ArrayList<>();

    public IncludeWhereClause where(String column) {
        return new IncludeWhereClause(this, column, WhereCondition.Conjunction.NONE);
    }

    public IncludeWhereClause and(String column) {
        return new IncludeWhereClause(this, column, WhereCondition.Conjunction.AND);
    }

    public IncludeOrderByClause orderBy(String column) {
        return new IncludeOrderByClause(this, column);
    }

    void addCondition(WhereCondition condition) {
        conditions.add(condition);
    }

    void addOrderBy(OrderByEntry entry) {
        orderBys.add(entry);
    }

    public static class IncludeWhereClause {
        private final IncludeQuery query;
        private final String column;
        private final WhereCondition.Conjunction conjunction;

        IncludeWhereClause(IncludeQuery query, String column, WhereCondition.Conjunction conjunction) {
            this.query = query;
            this.column = column;
            this.conjunction = conjunction;
        }

        public IncludeQuery eq(Object value) {
            return addCondition(WhereCondition.Operator.EQ, value);
        }

        public IncludeQuery neq(Object value) {
            return addCondition(WhereCondition.Operator.NEQ, value);
        }

        public IncludeQuery gt(Object value) {
            return addCondition(WhereCondition.Operator.GT, value);
        }

        public IncludeQuery gte(Object value) {
            return addCondition(WhereCondition.Operator.GTE, value);
        }

        public IncludeQuery lt(Object value) {
            return addCondition(WhereCondition.Operator.LT, value);
        }

        public IncludeQuery lte(Object value) {
            return addCondition(WhereCondition.Operator.LTE, value);
        }

        public IncludeQuery like(String pattern) {
            return addCondition(WhereCondition.Operator.LIKE, pattern);
        }

        public IncludeQuery isNull() {
            return addCondition(WhereCondition.Operator.IS_NULL, null);
        }

        public IncludeQuery isNotNull() {
            return addCondition(WhereCondition.Operator.IS_NOT_NULL, null);
        }

        private IncludeQuery addCondition(WhereCondition.Operator operator, Object value) {
            query.addCondition(new WhereCondition(column, operator, value, conjunction));
            return query;
        }
    }

    public static class IncludeOrderByClause {
        private final IncludeQuery query;
        private final String column;

        IncludeOrderByClause(IncludeQuery query, String column) {
            this.query = query;
            this.column = column;
        }

        public IncludeQuery asc() {
            query.addOrderBy(new OrderByEntry(column, true));
            return query;
        }

        public IncludeQuery desc() {
            query.addOrderBy(new OrderByEntry(column, false));
            return query;
        }
    }
}
