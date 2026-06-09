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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures.AbstractFixtureView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures.NotAViewFixture;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures.UnannotatedFixtureView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures.ValidFixtureView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewDiscoveryServiceTest {

    private static final String FIXTURES_PACKAGE =
            "tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures";

    @Test
    void emptyResultForPackageWithNoAnnotatedClasses() {
        ViewDiscoveryService service = new ViewDiscoveryService();

        Set<Class<? extends View>> result = service.discoverFromPackage(
                "tech.guilhermekaua.spigotboot.inventoryapi.nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void resultIsAlwaysNonNull() {
        ViewDiscoveryService service = new ViewDiscoveryService();

        Set<Class<? extends View>> result = service.discoverFromPackage(
                "com.example.does.not.exist");

        assertTrue(result.isEmpty(), "service should return empty set, not null");
    }

    @Test
    void discoversOnlyConcreteAnnotatedViewSubclasses() {
        ViewDiscoveryService service = new ViewDiscoveryService();

        Set<Class<? extends View>> result = service.discoverFromPackage(FIXTURES_PACKAGE);

        assertEquals(Collections.singleton(ValidFixtureView.class), result);
        // abstract @RegisterView subclass is excluded silently
        assertFalse(result.contains(AbstractFixtureView.class));
        // unannotated View subclass is not picked up by the annotation scan
        assertFalse(result.contains(UnannotatedFixtureView.class));
    }

    @Test
    void notAViewFixture_isExcludedAndLoggedSevere() {
        ViewDiscoveryService service = new ViewDiscoveryService();
        Logger logger = Logger.getLogger(ViewDiscoveryService.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);

        try {
            Set<Class<? extends View>> result = service.discoverFromPackage(FIXTURES_PACKAGE);

            assertFalse(result.contains(NotAViewFixture.class));
        } finally {
            logger.removeHandler(handler);
        }

        boolean severeLogged = records.stream().anyMatch(record ->
                record.getLevel() == Level.SEVERE
                        && record.getMessage().contains(NotAViewFixture.class.getName())
                        && record.getMessage().contains("does not extend View"));
        assertTrue(severeLogged,
                "expected a SEVERE boot-guard log naming " + NotAViewFixture.class.getName());
    }
}
