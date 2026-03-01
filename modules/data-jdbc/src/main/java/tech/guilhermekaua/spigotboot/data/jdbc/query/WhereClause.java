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

import java.util.Collection;

public class WhereClause<T> {
    private final SelectQuery<T> query;
    private final String column;
    private final WhereCondition.Conjunction conjunction;

    WhereClause(SelectQuery<T> query, String column, WhereCondition.Conjunction conjunction) {
        this.query = query;
        this.column = column;
        this.conjunction = conjunction;
    }

    public SelectQuery<T> eq(Object value) {
        return addCondition(WhereCondition.Operator.EQ, value);
    }

    public SelectQuery<T> neq(Object value) {
        return addCondition(WhereCondition.Operator.NEQ, value);
    }

    public SelectQuery<T> gt(Object value) {
        return addCondition(WhereCondition.Operator.GT, value);
    }

    public SelectQuery<T> gte(Object value) {
        return addCondition(WhereCondition.Operator.GTE, value);
    }

    public SelectQuery<T> lt(Object value) {
        return addCondition(WhereCondition.Operator.LT, value);
    }

    public SelectQuery<T> lte(Object value) {
        return addCondition(WhereCondition.Operator.LTE, value);
    }

    public SelectQuery<T> like(String pattern) {
        return addCondition(WhereCondition.Operator.LIKE, pattern);
    }

    public SelectQuery<T> in(Collection<?> values) {
        return addCondition(WhereCondition.Operator.IN, values);
    }

    public SelectQuery<T> isNull() {
        return addCondition(WhereCondition.Operator.IS_NULL, null);
    }

    public SelectQuery<T> isNotNull() {
        return addCondition(WhereCondition.Operator.IS_NOT_NULL, null);
    }

    private SelectQuery<T> addCondition(WhereCondition.Operator operator, Object value) {
        query.addCondition(new WhereCondition(column, operator, value, conjunction));
        return query;
    }
}
