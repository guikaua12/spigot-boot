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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RelationshipMetadata {
    private final Field field;
    private final RelationshipType type;
    private final List<RelationshipJoinColumn> joinColumns;
    private final Class<?> targetEntityClass;
    private final boolean collection;

    public RelationshipMetadata(
            Field field,
            RelationshipType type,
            List<RelationshipJoinColumn> joinColumns,
            Class<?> targetEntityClass,
            boolean collection
    ) {
        if (joinColumns == null || joinColumns.isEmpty()) {
            throw new IllegalArgumentException("Relationship metadata requires at least one join column");
        }

        this.field = field;
        this.type = type;
        this.joinColumns = Collections.unmodifiableList(new ArrayList<>(joinColumns));
        this.targetEntityClass = targetEntityClass;
        this.collection = collection;
    }

    public Field getField() {
        return field;
    }

    public RelationshipType getType() {
        return type;
    }

    public List<RelationshipJoinColumn> getJoinColumns() {
        return joinColumns;
    }

    public String getForeignKeyColumn() {
        if (joinColumns.size() != 1) {
            throw new IllegalStateException(
                    "Relationship '" + field.getName() + "' on " + field.getDeclaringClass().getName() +
                            " has multiple join columns. Use getJoinColumns() instead."
            );
        }

        return joinColumns.get(0).getColumnName();
    }

    public boolean hasSingleJoinColumn() {
        return joinColumns.size() == 1;
    }

    public Class<?> getTargetEntityClass() {
        return targetEntityClass;
    }

    public boolean isCollection() {
        return collection;
    }

    public enum RelationshipType {
        ONE_TO_MANY,
        MANY_TO_ONE
    }
}
