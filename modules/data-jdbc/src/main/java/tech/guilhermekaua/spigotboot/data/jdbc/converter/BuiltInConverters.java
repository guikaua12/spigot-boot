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

import tech.guilhermekaua.spigotboot.data.converter.AttributeConverter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

public final class BuiltInConverters {

    private BuiltInConverters() {
    }

    public static void registerAll(TypeConverterRegistry registry) {
        registry.register(BigDecimal.class, new BigDecimalConverter());
        registry.register(BigInteger.class, new BigIntegerConverter());
        registry.register(UUID.class, new UuidConverter());
        registry.register(Instant.class, new InstantConverter());
        registry.register(LocalDate.class, new LocalDateConverter());
        registry.register(LocalDateTime.class, new LocalDateTimeConverter());
        registry.register(LocalTime.class, new LocalTimeConverter());
        registry.register(OffsetDateTime.class, new OffsetDateTimeConverter());
        registry.register(OffsetTime.class, new OffsetTimeConverter());
        registry.register(ZonedDateTime.class, new ZonedDateTimeConverter());
        registry.register(Duration.class, new DurationConverter());
        registry.register(Date.class, new DateConverter());
        registry.register(Calendar.class, new CalendarConverter());
    }

    static class BigDecimalConverter implements AttributeConverter<BigDecimal, Object> {
        @Override
        public Object convertToDatabaseColumn(BigDecimal attribute) {
            return attribute;
        }

        @Override
        public BigDecimal convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            if (dbData instanceof BigDecimal) {
                return (BigDecimal) dbData;
            }

            if (dbData instanceof BigInteger) {
                return new BigDecimal((BigInteger) dbData);
            }

            try {
                return new BigDecimal(dbData.toString().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Unsupported database value for BigDecimal conversion: " + dbData.getClass().getName(), exception);
            }
        }
    }

    static class BigIntegerConverter implements AttributeConverter<BigInteger, Object> {
        @Override
        public Object convertToDatabaseColumn(BigInteger attribute) {
            return attribute == null ? null : new BigDecimal(attribute);
        }

        @Override
        public BigInteger convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            if (dbData instanceof BigInteger) {
                return (BigInteger) dbData;
            }

            if (dbData instanceof BigDecimal) {
                try {
                    return ((BigDecimal) dbData).toBigIntegerExact();
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("BigDecimal value cannot be represented as BigInteger without precision loss", exception);
                }
            }

            try {
                return new BigDecimal(dbData.toString().trim()).toBigIntegerExact();
            } catch (NumberFormatException | ArithmeticException exception) {
                throw new IllegalArgumentException("Unsupported database value for BigInteger conversion: " + dbData.getClass().getName(), exception);
            }
        }
    }

    static class UuidConverter implements AttributeConverter<UUID, String> {
        @Override
        public String convertToDatabaseColumn(UUID attribute) {
            return attribute == null ? null : attribute.toString();
        }

        @Override
        public UUID convertToEntityAttribute(String dbData) {
            return dbData == null ? null : UUID.fromString(dbData);
        }
    }

    static class InstantConverter implements AttributeConverter<Instant, Object> {
        @Override
        public Object convertToDatabaseColumn(Instant attribute) {
            return attribute == null ? null : Timestamp.from(attribute);
        }

        @Override
        public Instant convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            return toInstant(dbData, "Instant");
        }
    }

    static class LocalDateConverter implements AttributeConverter<LocalDate, Object> {
        @Override
        public Object convertToDatabaseColumn(LocalDate attribute) {
            return attribute == null ? null : java.sql.Date.valueOf(attribute);
        }

        @Override
        public LocalDate convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            if (dbData instanceof java.sql.Date) {
                return ((java.sql.Date) dbData).toLocalDate();
            }

            if (dbData instanceof Timestamp) {
                return ((Timestamp) dbData).toLocalDateTime().toLocalDate();
            }

            if (dbData instanceof Date) {
                return Instant.ofEpochMilli(((Date) dbData).getTime()).atZone(ZoneOffset.UTC).toLocalDate();
            }

            if (dbData instanceof String) {
                return parseLocalDate((String) dbData);
            }

            throw new IllegalArgumentException("Unsupported database value for LocalDate conversion: " + dbData.getClass().getName());
        }
    }

    static class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, Object> {
        @Override
        public Object convertToDatabaseColumn(LocalDateTime attribute) {
            return attribute == null ? null : Timestamp.valueOf(attribute);
        }

        @Override
        public LocalDateTime convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            if (dbData instanceof Timestamp) {
                return ((Timestamp) dbData).toLocalDateTime();
            }

            if (dbData instanceof Date) {
                return Instant.ofEpochMilli(((Date) dbData).getTime()).atOffset(ZoneOffset.UTC).toLocalDateTime();
            }

            if (dbData instanceof String) {
                return parseLocalDateTime((String) dbData);
            }

            throw new IllegalArgumentException("Unsupported database value for LocalDateTime conversion: " + dbData.getClass().getName());
        }
    }

    static class LocalTimeConverter implements AttributeConverter<LocalTime, Object> {
        @Override
        public Object convertToDatabaseColumn(LocalTime attribute) {
            return attribute == null ? null : Time.valueOf(attribute);
        }

        @Override
        public LocalTime convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            if (dbData instanceof Time) {
                return ((Time) dbData).toLocalTime();
            }

            if (dbData instanceof Timestamp) {
                return ((Timestamp) dbData).toLocalDateTime().toLocalTime();
            }

            if (dbData instanceof Date) {
                return Instant.ofEpochMilli(((Date) dbData).getTime()).atOffset(ZoneOffset.UTC).toLocalTime();
            }

            if (dbData instanceof String) {
                return parseLocalTime((String) dbData);
            }

            throw new IllegalArgumentException("Unsupported database value for LocalTime conversion: " + dbData.getClass().getName());
        }
    }

    static class OffsetDateTimeConverter implements AttributeConverter<OffsetDateTime, String> {
        @Override
        public String convertToDatabaseColumn(OffsetDateTime attribute) {
            return attribute == null ? null : attribute.toString();
        }

        @Override
        public OffsetDateTime convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }

            return OffsetDateTime.parse(dbData);
        }
    }

    static class OffsetTimeConverter implements AttributeConverter<OffsetTime, String> {
        @Override
        public String convertToDatabaseColumn(OffsetTime attribute) {
            return attribute == null ? null : attribute.toString();
        }

        @Override
        public OffsetTime convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }

            return OffsetTime.parse(dbData);
        }
    }

    static class ZonedDateTimeConverter implements AttributeConverter<ZonedDateTime, String> {
        @Override
        public String convertToDatabaseColumn(ZonedDateTime attribute) {
            return attribute == null ? null : attribute.toString();
        }

        @Override
        public ZonedDateTime convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }

            return ZonedDateTime.parse(dbData);
        }
    }

    static class DurationConverter implements AttributeConverter<Duration, Object> {
        @Override
        public Object convertToDatabaseColumn(Duration attribute) {
            return attribute == null ? null : attribute.toMillis();
        }

        @Override
        public Duration convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            if (dbData instanceof Duration) {
                return (Duration) dbData;
            }

            if (dbData instanceof Number) {
                return Duration.ofMillis(((Number) dbData).longValue());
            }

            if (dbData instanceof String) {
                String text = ((String) dbData).trim();
                try {
                    return Duration.ofMillis(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                    return Duration.parse(text);
                }
            }

            throw new IllegalArgumentException("Unsupported database value for Duration conversion: " + dbData.getClass().getName());
        }
    }

    static class DateConverter implements AttributeConverter<Date, Object> {
        @Override
        public Object convertToDatabaseColumn(Date attribute) {
            return attribute == null ? null : new Timestamp(attribute.getTime());
        }

        @Override
        public Date convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            if (dbData instanceof Date) {
                return new Date(((Date) dbData).getTime());
            }

            return Date.from(toInstant(dbData, "Date"));
        }
    }

    static class CalendarConverter implements AttributeConverter<Calendar, Object> {
        @Override
        public Object convertToDatabaseColumn(Calendar attribute) {
            return attribute == null ? null : Timestamp.from(attribute.toInstant());
        }

        @Override
        public Calendar convertToEntityAttribute(Object dbData) {
            if (dbData == null) {
                return null;
            }

            if (dbData instanceof Calendar) {
                Calendar source = (Calendar) dbData;
                Calendar clone = Calendar.getInstance(source.getTimeZone());
                clone.setTimeInMillis(source.getTimeInMillis());
                return clone;
            }

            Instant instant = toInstant(dbData, "Calendar");
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(instant.toEpochMilli());
            return calendar;
        }
    }

    private static Instant toInstant(Object dbData, String targetType) {
        if (dbData instanceof Instant) {
            return (Instant) dbData;
        }

        if (dbData instanceof Timestamp) {
            return ((Timestamp) dbData).toInstant();
        }

        if (dbData instanceof Date) {
            return Instant.ofEpochMilli(((Date) dbData).getTime());
        }

        if (dbData instanceof Number) {
            return Instant.ofEpochMilli(((Number) dbData).longValue());
        }

        if (dbData instanceof String) {
            return parseInstant((String) dbData);
        }

        throw new IllegalArgumentException("Unsupported database value for " + targetType + " conversion: " + dbData.getClass().getName());
    }

    private static Instant parseInstant(String dbData) {
        String trimmed = dbData.trim();

        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Timestamp.valueOf(normalizeDateTimeLiteral(trimmed)).toInstant();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Instant.ofEpochMilli(Long.parseLong(trimmed));
        } catch (NumberFormatException ignored) {
        }

        throw new IllegalArgumentException("Unsupported database value for Instant conversion: " + dbData);
    }

    private static LocalDate parseLocalDate(String dbData) {
        String trimmed = dbData.trim();

        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(trimmed).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Timestamp.valueOf(normalizeDateTimeLiteral(trimmed)).toLocalDateTime().toLocalDate();
        } catch (IllegalArgumentException ignored) {
        }

        throw new IllegalArgumentException("Unsupported database value for LocalDate conversion: " + dbData);
    }

    private static LocalDateTime parseLocalDateTime(String dbData) {
        String trimmed = dbData.trim();

        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Instant.parse(trimmed).atOffset(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Timestamp.valueOf(normalizeDateTimeLiteral(trimmed)).toLocalDateTime();
        } catch (IllegalArgumentException ignored) {
        }

        throw new IllegalArgumentException("Unsupported database value for LocalDateTime conversion: " + dbData);
    }

    private static LocalTime parseLocalTime(String dbData) {
        String trimmed = dbData.trim();

        try {
            return LocalTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetTime.parse(trimmed).toLocalTime();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed).toLocalTime();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Timestamp.valueOf(normalizeDateTimeLiteral(trimmed)).toLocalDateTime().toLocalTime();
        } catch (IllegalArgumentException ignored) {
        }

        throw new IllegalArgumentException("Unsupported database value for LocalTime conversion: " + dbData);
    }

    private static String normalizeDateTimeLiteral(String dbData) {
        String normalized = dbData.trim().replace('T', ' ');
        if (normalized.endsWith("Z")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
