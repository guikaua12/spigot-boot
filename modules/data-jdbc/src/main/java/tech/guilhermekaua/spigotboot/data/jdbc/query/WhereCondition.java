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
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@Getter
@RequiredArgsConstructor
public class WhereCondition {
    private final String column;
    private final Operator operator;
    private final @Nullable Object value;
    private final Conjunction conjunction;

    public enum Operator {
        EQ("="),
        NEQ("!="),
        GT(">"),
        GTE(">="),
        LT("<"),
        LTE("<="),
        LIKE("LIKE"),
        IN("IN"),
        IS_NULL("IS NULL"),
        IS_NOT_NULL("IS NOT NULL");

        @Getter
        private final String sql;

        Operator(String sql) {
            this.sql = sql;
        }

        public boolean isNoValue() {
            return this == IS_NULL || this == IS_NOT_NULL;
        }

        public boolean isCollection() {
            return this == IN;
        }
    }

    public enum Conjunction {
        AND,
        OR,
        NONE
    }

    public int getParameterCount() {
        if (operator.isNoValue()) return 0;
        if (operator.isCollection() && value instanceof Collection<?>) {
            return ((Collection<?>) value).size();
        }
        return 1;
    }
}
