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
package tech.guilhermekaua.spigotboot.data.jdbc.dialect;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialectTypeMappingTest {
    @Nested
    class MySqlTests {
        private final MySQLDialect dialect = new MySQLDialect();

        @Test
        void mapsBigNumericTypesToDecimal() {
            assertEquals("DECIMAL(38, 18)", dialect.mapJavaTypeToSqlType(BigDecimal.class));
            assertEquals("DECIMAL(38, 0)", dialect.mapJavaTypeToSqlType(BigInteger.class));
        }

        @Test
        void mapsDurationAndLocalTimeToNativeTypes() {
            assertEquals("BIGINT", dialect.mapJavaTypeToSqlType(Duration.class));
            assertEquals("TIME", dialect.mapJavaTypeToSqlType(LocalTime.class));
        }
    }

    @Nested
    class SQLiteTests {
        private final SQLiteDialect dialect = new SQLiteDialect();

        @Test
        void mapsBigNumericTypesToNumericAffinity() {
            assertEquals("NUMERIC", dialect.mapJavaTypeToSqlType(BigDecimal.class));
            assertEquals("NUMERIC", dialect.mapJavaTypeToSqlType(BigInteger.class));
        }

        @Test
        void mapsDurationAndLocalTimeForSqliteStorage() {
            assertEquals("INTEGER", dialect.mapJavaTypeToSqlType(Duration.class));
            assertEquals("TEXT", dialect.mapJavaTypeToSqlType(LocalTime.class));
        }
    }
}
