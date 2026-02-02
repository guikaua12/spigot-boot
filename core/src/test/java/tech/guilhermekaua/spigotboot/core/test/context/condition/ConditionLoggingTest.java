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
package tech.guilhermekaua.spigotboot.core.test.context.condition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnClass;
import tech.guilhermekaua.spigotboot.core.context.condition.*;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class ConditionLoggingTest {
    private Logger logger;
    private TestLogHandler logHandler;
    private ConditionContext context;
    private Level previousLevel;
    private boolean previousUseParentHandlers;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger(ConditionEvaluator.class.getName());
        previousLevel = logger.getLevel();
        previousUseParentHandlers = logger.getUseParentHandlers();

        logHandler = new TestLogHandler();
        logger.addHandler(logHandler);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);

        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();
        context = new SimpleConditionContext(registry, null, getClass().getClassLoader());
    }

    @AfterEach
    void tearDown() {
        logger.removeHandler(logHandler);
        logger.setLevel(previousLevel);
        logger.setUseParentHandlers(previousUseParentHandlers);
        ConditionEvaluator.setDebugReport(null);
    }

    @Test
    void shouldLogWarningWhenConditionFailsWithWarningLevel() {
        @ConditionalOnClass(value = "com.nonexistent.Class", logLevel = LogLevel.WARNING)
        class TestClass {
        }

        boolean shouldSkip = ConditionEvaluator.shouldSkip(TestClass.class, context, "TestSource");

        assertTrue(shouldSkip);
        assertEquals(1, logHandler.getRecords().size());
        LogRecord record = logHandler.getRecords().get(0);
        assertEquals(Level.WARNING, record.getLevel());
        assertTrue(record.getMessage().contains("TestSource"));
        assertTrue(record.getMessage().contains("TestClass"));
    }

    @Test
    void shouldNotLogWhenConditionFailsWithSilentLevel() {
        @ConditionalOnClass(value = "com.nonexistent.Class", logLevel = LogLevel.SILENT)
        class TestClass {
        }

        boolean shouldSkip = ConditionEvaluator.shouldSkip(TestClass.class, context, "TestSource");

        assertTrue(shouldSkip);
        assertEquals(0, logHandler.getRecords().size());
    }

    @Test
    void shouldLogFineWhenConditionFailsWithDebugLevel() {
        @ConditionalOnClass(value = "com.nonexistent.Class", logLevel = LogLevel.DEBUG)
        class TestClass {
        }

        boolean shouldSkip = ConditionEvaluator.shouldSkip(TestClass.class, context, "TestSource");

        assertTrue(shouldSkip);
        assertEquals(1, logHandler.getRecords().size());
        LogRecord record = logHandler.getRecords().get(0);
        assertEquals(Level.FINE, record.getLevel());
        assertTrue(record.getMessage().contains("TestSource"));
        assertTrue(record.getMessage().contains("TestClass"));
    }

    @Test
    void shouldLogInfoWhenConditionFailsWithInfoLevel() {
        @ConditionalOnClass(value = "com.nonexistent.Class", logLevel = LogLevel.INFO)
        class TestClass {
        }

        boolean shouldSkip = ConditionEvaluator.shouldSkip(TestClass.class, context, "TestSource");

        assertTrue(shouldSkip);
        assertEquals(1, logHandler.getRecords().size());
        LogRecord record = logHandler.getRecords().get(0);
        assertEquals(Level.INFO, record.getLevel());
        assertTrue(record.getMessage().contains("TestSource"));
        assertTrue(record.getMessage().contains("TestClass"));
    }

    @Test
    void shouldNotLogWhenConditionPasses() {
        @ConditionalOnClass(value = "java.lang.String", logLevel = LogLevel.WARNING)
        class TestClass {
        }

        boolean shouldSkip = ConditionEvaluator.shouldSkip(TestClass.class, context, "TestSource");

        assertFalse(shouldSkip);
        assertEquals(0, logHandler.getRecords().size());
    }

    @Test
    void shouldNotLogWhenSourceIsNull() {
        @ConditionalOnClass(value = "com.nonexistent.Class", logLevel = LogLevel.WARNING)
        class TestClass {
        }

        boolean shouldSkip = ConditionEvaluator.shouldSkip(TestClass.class, context, null);

        assertTrue(shouldSkip);
        assertEquals(0, logHandler.getRecords().size());
    }

    @Test
    void debugReportRecordsBothMatchesAndSkips() {
        ConditionDebugReport report = new ConditionDebugReport();
        ConditionEvaluator.setDebugReport(report);

        @ConditionalOnClass(value = "java.lang.String")
        class PassingClass {
        }

        @ConditionalOnClass(value = "com.nonexistent.Class")
        class FailingClass {
        }

        ConditionEvaluator.shouldSkip(PassingClass.class, context, "TestSource1");
        ConditionEvaluator.shouldSkip(FailingClass.class, context, "TestSource2");

        assertFalse(report.isEmpty());
        assertEquals(1, report.getMatchedSummary().size());
        assertEquals(1, report.getSkippedSummary().size());

        assertTrue(report.getMatchedSummary().get(0).contains("TestSource1"));
        assertTrue(report.getMatchedSummary().get(0).contains("PassingClass"));

        assertTrue(report.getSkippedSummary().get(0).contains("TestSource2"));
        assertTrue(report.getSkippedSummary().get(0).contains("FailingClass"));
    }

    @Test
    void debugReportPrintsFormattedOutput() {
        ConditionDebugReport report = new ConditionDebugReport();
        ConditionEvaluator.setDebugReport(report);

        @ConditionalOnClass(value = "java.lang.String")
        class PassingClass {
        }

        @ConditionalOnClass(value = "com.nonexistent.Class")
        class FailingClass {
        }

        ConditionEvaluator.shouldSkip(PassingClass.class, context, "Source1");
        ConditionEvaluator.shouldSkip(FailingClass.class, context, "Source2");

        TestLogHandler reportHandler = new TestLogHandler();
        Logger reportLogger = Logger.getLogger("test.report");
        reportLogger.addHandler(reportHandler);
        reportLogger.setLevel(Level.ALL);
        reportLogger.setUseParentHandlers(false);

        report.print(reportLogger);

        List<LogRecord> records = reportHandler.getRecords();
        assertTrue(records.size() > 0);

        String allMessages = records.stream()
                .map(LogRecord::getMessage)
                .reduce("", (a, b) -> a + "\n" + b);

        assertTrue(allMessages.contains("Condition Evaluation Report"));
        assertTrue(allMessages.contains("Matched Conditions"));
        assertTrue(allMessages.contains("Skipped Conditions"));
    }

    private static class TestLogHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        public List<LogRecord> getRecords() {
            return records;
        }
    }
}
