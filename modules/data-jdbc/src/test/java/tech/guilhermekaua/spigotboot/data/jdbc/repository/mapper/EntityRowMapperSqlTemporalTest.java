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
package tech.guilhermekaua.spigotboot.data.jdbc.repository.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Column;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Id;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.IdStrategy;
import tech.guilhermekaua.spigotboot.data.jdbc.annotation.Table;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.BuiltInConverters;
import tech.guilhermekaua.spigotboot.data.jdbc.converter.TypeConverterRegistry;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadata;
import tech.guilhermekaua.spigotboot.data.jdbc.metadata.EntityMetadataParser;

import java.sql.ResultSet;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityRowMapperSqlTemporalTest {
    private EntityMetadata metadata;

    @BeforeEach
    void setUp() {
        TypeConverterRegistry registry = new TypeConverterRegistry();
        BuiltInConverters.registerAll(registry);
        metadata = new EntityMetadataParser(registry).parse(SqlTemporalEntity.class);
    }

    @Test
    void mapsMySqlJavaTimeValuesIntoSqlTemporalFields() throws Exception {
        // mysql-connector-j 8.x returns java.time.* from DATETIME/DATE/TIME columns
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 3, 12, 15, 45);
        LocalDate eventDate = LocalDate.of(2026, 3, 3);
        LocalTime eventTime = LocalTime.of(9, 5, 7);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id")).thenReturn(1L);
        when(rs.getObject("created_at")).thenReturn(createdAt);
        when(rs.getObject("event_date")).thenReturn(eventDate);
        when(rs.getObject("event_time")).thenReturn(eventTime);

        EntityRowMapper<SqlTemporalEntity> mapper = new EntityRowMapper<>(metadata);
        SqlTemporalEntity entity = mapper.mapRow(rs);

        assertEquals(Timestamp.valueOf(createdAt), entity.getCreatedAt());
        assertEquals(java.sql.Date.valueOf(eventDate), entity.getEventDate());
        assertEquals(Time.valueOf(eventTime), entity.getEventTime());
    }

    @Table("events")
    public static final class SqlTemporalEntity {
        @Id(strategy = IdStrategy.IDENTITY)
        @Column("id")
        private Long id;

        @Column("created_at")
        private Timestamp createdAt;

        @Column("event_date")
        private java.sql.Date eventDate;

        @Column("event_time")
        private Time eventTime;

        public SqlTemporalEntity() {
        }

        public Long getId() {
            return id;
        }

        public Timestamp getCreatedAt() {
            return createdAt;
        }

        public java.sql.Date getEventDate() {
            return eventDate;
        }

        public Time getEventTime() {
            return eventTime;
        }
    }
}
