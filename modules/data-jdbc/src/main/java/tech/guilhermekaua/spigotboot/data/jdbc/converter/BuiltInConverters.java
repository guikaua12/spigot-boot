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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class BuiltInConverters {

    private BuiltInConverters() {
    }

    public static void registerAll(TypeConverterRegistry registry) {
        registry.register(UUID.class, new UuidConverter());
        registry.register(Instant.class, new InstantConverter());
        registry.register(LocalDate.class, new LocalDateConverter());
        registry.register(LocalDateTime.class, new LocalDateTimeConverter());
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

    static class InstantConverter implements AttributeConverter<Instant, Timestamp> {
        @Override
        public Timestamp convertToDatabaseColumn(Instant attribute) {
            return attribute == null ? null : Timestamp.from(attribute);
        }

        @Override
        public Instant convertToEntityAttribute(Timestamp dbData) {
            return dbData == null ? null : dbData.toInstant();
        }
    }

    static class LocalDateConverter implements AttributeConverter<LocalDate, java.sql.Date> {
        @Override
        public java.sql.Date convertToDatabaseColumn(LocalDate attribute) {
            return attribute == null ? null : java.sql.Date.valueOf(attribute);
        }

        @Override
        public LocalDate convertToEntityAttribute(java.sql.Date dbData) {
            return dbData == null ? null : dbData.toLocalDate();
        }
    }

    static class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, Timestamp> {
        @Override
        public Timestamp convertToDatabaseColumn(LocalDateTime attribute) {
            return attribute == null ? null : Timestamp.valueOf(attribute);
        }

        @Override
        public LocalDateTime convertToEntityAttribute(Timestamp dbData) {
            return dbData == null ? null : dbData.toLocalDateTime();
        }
    }
}
