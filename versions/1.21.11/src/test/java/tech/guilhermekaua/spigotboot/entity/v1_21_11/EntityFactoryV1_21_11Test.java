/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.v1_21_11;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFactoryV1_21_11Test {

    @Test
    void recreateModernSectionCallback_shouldBindTheReplacementEntity() {
        StubSectionManager manager = new StubSectionManager();
        StubSection section = new StubSection();
        StubEntity oldEntity = new StubEntity(10);
        StubEntity replacementEntity = new StubEntity(10);
        StubCallback callback = new StubCallback(manager, oldEntity, 42L, section);

        Object rebound = EntityFactoryV1_21_11.recreateModernSectionCallback(callback, replacementEntity);

        StubCallback reboundCallback = assertInstanceOf(StubCallback.class, rebound);
        assertSame(manager, reboundCallback.this$0);
        assertSame(replacementEntity, reboundCallback.entity);
        assertEquals(42L, reboundCallback.currentSectionKey);
        assertSame(section, reboundCallback.currentSection);
    }

    @Test
    void retargetModernSectionCallback_shouldReuseTheExistingCallback() {
        StubSectionManager manager = new StubSectionManager();
        StubSection section = new StubSection();
        StubEntity oldEntity = new StubEntity(14);
        StubEntity replacementEntity = new StubEntity(14);
        StubCallback callback = new StubCallback(manager, oldEntity, 9L, section);

        Object rebound = EntityFactoryV1_21_11.retargetModernSectionCallback(
                callback,
                oldEntity,
                replacementEntity
        );

        StubCallback reboundCallback = assertInstanceOf(StubCallback.class, rebound);
        assertSame(callback, reboundCallback);
        assertSame(replacementEntity, reboundCallback.entity);
    }

    @Test
    void migrateModernSectionMembership_shouldReplaceTheTrackedEntity() {
        StubSection section = new StubSection();
        StubEntity oldEntity = new StubEntity(12);
        StubEntity replacementEntity = new StubEntity(12);
        StubCallback callback = new StubCallback(new StubSectionManager(), oldEntity, 7L, section);
        section.add(oldEntity);

        EntityFactoryV1_21_11.migrateModernSectionMembership(callback, oldEntity, replacementEntity);

        assertFalse(section.contains(oldEntity));
        assertTrue(section.contains(replacementEntity));
        assertEquals(1, section.size());
    }

    @Test
    void replaceManagedCollectionEntry_shouldOnlySwapWhenTheOriginalIsPresent() {
        StubEntity oldEntity = new StubEntity(3);
        StubEntity replacementEntity = new StubEntity(3);
        StubEntityTickList tickList = new StubEntityTickList();
        tickList.add(oldEntity);

        EntityFactoryV1_21_11.replaceManagedCollectionEntry(tickList, oldEntity, replacementEntity);

        assertFalse(tickList.contains(oldEntity));
        assertTrue(tickList.contains(replacementEntity));

        StubNavigatingMobs navigatingMobs = new StubNavigatingMobs();
        EntityFactoryV1_21_11.replaceManagedCollectionEntry(navigatingMobs, oldEntity, replacementEntity);
        assertFalse(navigatingMobs.contains(replacementEntity));
    }

    private static final class StubSectionManager {
    }

    private static final class StubEntity {
        private final int id;

        private StubEntity(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    private static final class StubSection {
        private final Set<StubEntity> entities = new LinkedHashSet<StubEntity>();

        public void add(StubEntity entity) {
            entities.add(entity);
        }

        public boolean remove(StubEntity entity) {
            return entities.remove(entity);
        }

        public Stream<StubEntity> getEntities() {
            return entities.stream();
        }

        public boolean contains(StubEntity entity) {
            return entities.contains(entity);
        }

        public int size() {
            return entities.size();
        }
    }

    private static final class StubCallback {
        private final StubSectionManager this$0;
        private final StubEntity entity;
        private final long currentSectionKey;
        private final StubSection currentSection;

        private StubCallback(
                StubSectionManager manager,
                StubEntity entity,
                long currentSectionKey,
                StubSection currentSection
        ) {
            this.this$0 = manager;
            this.entity = entity;
            this.currentSectionKey = currentSectionKey;
            this.currentSection = currentSection;
        }
    }

    private static final class StubEntityTickList {
        private final Set<StubEntity> entities = new LinkedHashSet<StubEntity>();

        public void add(StubEntity entity) {
            entities.add(entity);
        }

        public void remove(StubEntity entity) {
            entities.remove(entity);
        }

        public boolean contains(StubEntity entity) {
            return entities.contains(entity);
        }
    }

    private static final class StubNavigatingMobs extends LinkedHashSet<StubEntity> {
    }
}
