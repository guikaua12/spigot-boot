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
package tech.guilhermekaua.spigotboot.data.jdbc.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.data.converter.AttributeConverter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BuiltInConvertersTest {
    private TypeConverterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TypeConverterRegistry();
        BuiltInConverters.registerAll(registry);
    }

    @Nested
    class RegistrationTests {
        @Test
        void registersExpectedConverters() {
            assertTrue(registry.hasConverter(BigDecimal.class));
            assertTrue(registry.hasConverter(BigInteger.class));
            assertTrue(registry.hasConverter(UUID.class));
            assertTrue(registry.hasConverter(Instant.class));
            assertTrue(registry.hasConverter(LocalDate.class));
            assertTrue(registry.hasConverter(LocalDateTime.class));
            assertTrue(registry.hasConverter(LocalTime.class));
            assertTrue(registry.hasConverter(OffsetDateTime.class));
            assertTrue(registry.hasConverter(OffsetTime.class));
            assertTrue(registry.hasConverter(ZonedDateTime.class));
            assertTrue(registry.hasConverter(Duration.class));
            assertTrue(registry.hasConverter(Date.class));
            assertTrue(registry.hasConverter(Calendar.class));
        }
    }

    @Nested
    class NumericConverterTests {
        @Test
        void bigDecimalConverterSupportsStringAndNumericValues() {
            AttributeConverter<BigDecimal, Object> converter = converterFor(BigDecimal.class);

            assertEquals(new BigDecimal("123.45"), converter.convertToEntityAttribute("123.45"));
            assertEquals(new BigDecimal("123.45"), converter.convertToEntityAttribute(123.45d));
        }

        @Test
        void bigIntegerConverterUsesExactNumericConversion() {
            AttributeConverter<BigInteger, Object> converter = converterFor(BigInteger.class);

            assertEquals(new BigInteger("9876543210123456789"), converter.convertToEntityAttribute("9876543210123456789"));
            assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute(new BigDecimal("10.2")));
        }
    }

    @Nested
    class TemporalConverterTests {
        @Test
        void instantConverterReadsTimestampAndIsoString() {
            AttributeConverter<Instant, Object> converter = converterFor(Instant.class);
            Instant instant = Instant.parse("2026-03-03T12:34:56Z");

            assertEquals(instant, converter.convertToEntityAttribute(Timestamp.from(instant)));
            assertEquals(instant, converter.convertToEntityAttribute(instant.toString()));
        }

        @Test
        void instantConverterReadsLocalDateTimeFromMySql() {
            AttributeConverter<Instant, Object> converter = converterFor(Instant.class);
            Instant instant = Instant.parse("2026-03-03T12:34:56Z");
            // mysql-connector-j 8.x returns java.time.LocalDateTime from a DATETIME/TIMESTAMP column
            LocalDateTime mysqlValue = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

            assertEquals(instant, converter.convertToEntityAttribute(mysqlValue));
        }

        @Test
        void localDateTimeConverterReadsJdbcLiteral() {
            AttributeConverter<LocalDateTime, Object> converter = converterFor(LocalDateTime.class);
            LocalDateTime expected = LocalDateTime.of(2026, 3, 3, 12, 15, 45);

            assertEquals(expected, converter.convertToEntityAttribute("2026-03-03 12:15:45"));
        }

        @Test
        void localDateTimeConverterReadsLocalDateTimeFromMySql() {
            AttributeConverter<LocalDateTime, Object> converter = converterFor(LocalDateTime.class);
            LocalDateTime expected = LocalDateTime.of(2026, 3, 3, 12, 15, 45);

            // mysql-connector-j 8.x returns java.time.LocalDateTime from a DATETIME column
            assertEquals(expected, converter.convertToEntityAttribute(expected));
        }

        @Test
        void localTimeConverterReadsTimeValues() {
            AttributeConverter<LocalTime, Object> converter = converterFor(LocalTime.class);
            LocalTime expected = LocalTime.of(9, 5, 7);

            assertEquals(expected, converter.convertToEntityAttribute(Time.valueOf(expected)));
            assertEquals(expected, converter.convertToEntityAttribute("09:05:07"));
        }

        @Test
        void localTimeConverterReadsLocalTimeFromMySql() {
            AttributeConverter<LocalTime, Object> converter = converterFor(LocalTime.class);
            LocalTime expected = LocalTime.of(9, 5, 7);

            // mysql-connector-j 8.x returns java.time.LocalTime from a TIME column
            assertEquals(expected, converter.convertToEntityAttribute(expected));
        }

        @Test
        void offsetAndZonedConvertersRoundTripIsoValues() {
            AttributeConverter<OffsetDateTime, String> offsetDateTimeConverter = converterFor(OffsetDateTime.class);
            AttributeConverter<OffsetTime, String> offsetTimeConverter = converterFor(OffsetTime.class);
            AttributeConverter<ZonedDateTime, String> zonedDateTimeConverter = converterFor(ZonedDateTime.class);

            OffsetDateTime offsetDateTime = OffsetDateTime.parse("2026-03-03T10:30:15+02:00");
            OffsetTime offsetTime = OffsetTime.parse("10:30:15+02:00");
            ZonedDateTime zonedDateTime = ZonedDateTime.parse("2026-03-03T10:30:15+02:00[Europe/Paris]");

            assertEquals(offsetDateTime, offsetDateTimeConverter.convertToEntityAttribute(offsetDateTimeConverter.convertToDatabaseColumn(offsetDateTime)));
            assertEquals(offsetTime, offsetTimeConverter.convertToEntityAttribute(offsetTimeConverter.convertToDatabaseColumn(offsetTime)));
            assertEquals(zonedDateTime, zonedDateTimeConverter.convertToEntityAttribute(zonedDateTimeConverter.convertToDatabaseColumn(zonedDateTime)));
        }

        @Test
        void durationConverterReadsNumericAndIsoValues() {
            AttributeConverter<Duration, Object> converter = converterFor(Duration.class);

            assertEquals(Duration.ofNanos(1500), converter.convertToEntityAttribute(1500L));
            assertEquals(Duration.ofNanos(2500), converter.convertToEntityAttribute("2500"));
            assertEquals(Duration.ofMinutes(5), converter.convertToEntityAttribute("PT5M"));
        }

        @Test
        void durationConverterRoundTripsSubMillisecondPrecision() {
            AttributeConverter<Duration, Object> converter = converterFor(Duration.class);
            Duration value = Duration.ofNanos(1_234_567);

            Object dbValue = converter.convertToDatabaseColumn(value);

            assertEquals(1_234_567L, dbValue);
            assertEquals(value, converter.convertToEntityAttribute(dbValue));
        }

        @Test
        void localDateConverterReadsDateValues() {
            AttributeConverter<LocalDate, Object> converter = converterFor(LocalDate.class);

            assertEquals(LocalDate.of(2026, 3, 3), converter.convertToEntityAttribute("2026-03-03"));
        }

        @Test
        void localDateConverterReadsLocalDateFromMySql() {
            AttributeConverter<LocalDate, Object> converter = converterFor(LocalDate.class);
            LocalDate expected = LocalDate.of(2026, 3, 3);

            // mysql-connector-j 8.x returns java.time.LocalDate from a DATE column
            assertEquals(expected, converter.convertToEntityAttribute(expected));
        }
    }

    @Nested
    class LegacyConverterTests {
        @Test
        void dateConverterRoundTripsTimestamp() {
            AttributeConverter<Date, Object> converter = converterFor(Date.class);
            Date value = new Date(1710000000123L);

            Object dbValue = converter.convertToDatabaseColumn(value);
            assertNotNull(dbValue);

            Date mapped = converter.convertToEntityAttribute(dbValue);
            assertEquals(value.getTime(), mapped.getTime());
        }

        @Test
        void dateConverterReadsLocalDateTimeFromMySql() {
            AttributeConverter<Date, Object> converter = converterFor(Date.class);
            Date value = new Date(1710000000123L);
            // mysql-connector-j 8.x returns java.time.LocalDateTime from a DATETIME/TIMESTAMP column
            LocalDateTime mysqlValue = LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());

            Date mapped = converter.convertToEntityAttribute(mysqlValue);
            assertEquals(value.getTime(), mapped.getTime());
        }

        @Test
        void calendarConverterRoundTripsTimestamp() {
            AttributeConverter<Calendar, Object> converter = converterFor(Calendar.class);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(1710000000456L);

            Object dbValue = converter.convertToDatabaseColumn(calendar);
            assertNotNull(dbValue);

            Calendar mapped = converter.convertToEntityAttribute(dbValue);
            assertEquals(calendar.getTimeInMillis(), mapped.getTimeInMillis());
        }

        @Test
        void calendarConverterReadsLocalDateTimeFromMySql() {
            AttributeConverter<Calendar, Object> converter = converterFor(Calendar.class);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(1710000000456L);
            // mysql-connector-j 8.x returns java.time.LocalDateTime from a DATETIME/TIMESTAMP column
            LocalDateTime mysqlValue = LocalDateTime.ofInstant(calendar.toInstant(), ZoneId.systemDefault());

            Calendar mapped = converter.convertToEntityAttribute(mysqlValue);
            assertEquals(calendar.getTimeInMillis(), mapped.getTimeInMillis());
        }
    }

    @SuppressWarnings("unchecked")
    private <X, Y> AttributeConverter<X, Y> converterFor(Class<X> type) {
        AttributeConverter<?, ?> converter = registry.getConverter(type);
        assertNotNull(converter);
        return (AttributeConverter<X, Y>) converter;
    }
}
