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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Column;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Id;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.IdStrategy;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Query;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Table;
import tech.guilhermekaua.spigotboot.data.jdbc.connection.ConnectionProvider;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.BuiltInConverters;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.Dialect;
import tech.guilhermekaua.spigotboot.data.jdbc.dialect.SQLiteDialect;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataParser;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataRegistry;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryMethodHandlerScalarSqlTemporalTest {
    private TypeConverterRegistry converterRegistry;
    private EntityMetadataRegistry metadataRegistry;
    private EntityMetadata entityMetadata;
    private Dialect dialect;

    @BeforeEach
    void setUp() {
        converterRegistry = new TypeConverterRegistry();
        BuiltInConverters.registerAll(converterRegistry);
        metadataRegistry = new EntityMetadataRegistry(new EntityMetadataParser(converterRegistry));
        entityMetadata = metadataRegistry.getOrParse(EventEntity.class);
        dialect = new SQLiteDialect();
    }

    @Test
    void scalarTimestampReturnReadsLocalDateTimeFromMySql() throws Exception {
        // mysql-connector-j 8.x returns java.time.LocalDateTime from a DATETIME/TIMESTAMP column
        LocalDateTime mysqlValue = LocalDateTime.of(2026, 3, 3, 12, 15, 45);

        Object result = executeScalar("latestCreatedAt", mysqlValue);

        assertEquals(Timestamp.valueOf(mysqlValue), result);
    }

    @Test
    void scalarDateReturnReadsLocalDateFromMySql() throws Exception {
        // mysql-connector-j 8.x returns java.time.LocalDate from a DATE column
        LocalDate mysqlValue = LocalDate.of(2026, 3, 3);

        Object result = executeScalar("earliestEventDate", mysqlValue);

        assertEquals(java.sql.Date.class, result.getClass());
        assertEquals(java.sql.Date.valueOf(mysqlValue), result);
    }

    @Test
    void scalarTimeReturnReadsLocalTimeFromMySql() throws Exception {
        // mysql-connector-j 8.x returns java.time.LocalTime from a TIME column
        LocalTime mysqlValue = LocalTime.of(9, 5, 7);

        Object result = executeScalar("earliestEventTime", mysqlValue);

        assertEquals(Time.class, result.getClass());
        assertEquals(Time.valueOf(mysqlValue), result);
    }

    private Object executeScalar(String methodName, Object dbValue) throws Exception {
        ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(connectionProvider.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(1)).thenReturn(dbValue);

        QueryMethodHandler handler = new QueryMethodHandler(connectionProvider, metadataRegistry, converterRegistry);
        Method method = EventRepository.class.getMethod(methodName);
        return handler.execute(method, new Object[0], entityMetadata, dialect);
    }

    @Table("events")
    public static final class EventEntity {
        @Id(strategy = IdStrategy.IDENTITY)
        @Column("id")
        private Long id;

        @Column("created_at")
        private Timestamp createdAt;

        public EventEntity() {
        }
    }

    interface EventRepository {
        @Query("SELECT created_at FROM events ORDER BY created_at DESC LIMIT 1")
        Timestamp latestCreatedAt();

        @Query("SELECT event_date FROM events ORDER BY event_date ASC LIMIT 1")
        java.sql.Date earliestEventDate();

        @Query("SELECT event_time FROM events ORDER BY event_time ASC LIMIT 1")
        Time earliestEventTime();
    }
}
