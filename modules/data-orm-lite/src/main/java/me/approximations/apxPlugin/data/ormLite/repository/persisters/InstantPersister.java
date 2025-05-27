package me.approximations.apxPlugin.data.ormLite.repository.persisters;

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