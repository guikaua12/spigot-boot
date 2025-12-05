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
package tech.guilhermekaua.spigotboot.data.ormLite.persisters;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.BaseDataType;
import com.j256.ormlite.support.DatabaseResults;
import lombok.Getter;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public class InstantPersister extends BaseDataType {
    @Getter
    private static final InstantPersister singleton = new InstantPersister();

    private InstantPersister() {
        super(SqlType.DATE, new Class<?>[]{Instant.class});
    }

    @Override
    public Object parseDefaultString(FieldType fieldType, String defaultStr) throws SQLException {
        try {
            return Timestamp.valueOf(defaultStr).toInstant();
        } catch (IllegalArgumentException e) {
            throw new SQLException("Cannot parse default string '" + defaultStr + "' to Instant", e);
        }
    }

    @Override
    public Object resultToSqlArg(FieldType fieldType, DatabaseResults results, int columnPos) throws SQLException {
        Timestamp timestamp = results.getTimestamp(columnPos);
        return timestamp == null ? null : timestamp.toInstant();
    }

    @Override
    public Object javaToSqlArg(FieldType fieldType, Object javaObject) {
        if (javaObject == null) {
            return null;
        }
        Instant instant = (Instant) javaObject;
        return Timestamp.from(instant);
    }

    @Override
    public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos) {
        if (sqlArg == null) {
            return null;
        }
        if (sqlArg instanceof Timestamp) {
            return ((Timestamp) sqlArg).toInstant();
        }

        if (sqlArg instanceof Instant) {
            return sqlArg;
        }
        throw new IllegalArgumentException("Cannot convert SQL argument of type " + sqlArg.getClass().getName() + " to Instant");
    }
}