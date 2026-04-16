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
package tech.guilhermekaua.spigotboot.versions.runtime.nativebridge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionSupportFindFieldHierarchyTest {

    @Test
    void findFieldOfType_returnsParentFieldWhenChildShadowsWithWrongType() throws Exception {
        Field field = ReflectionSupport.findFieldOfType(
                WrongTypeShadowChild.class,
                List.class,
                "passengers"
        );

        assertNotNull(field, "expected to resolve the List-typed parent field");
        assertSame(WrongTypeShadowParent.class, field.getDeclaringClass(),
                "child's Object-typed shadow must be skipped");
        assertSame(List.class, field.getType());

        Object value = field.get(new WrongTypeShadowChild());
        assertTrue(value instanceof ArrayList);
    }

    @Test
    void findFieldOfType_returnsChildFieldWhenChildShadowsWithCorrectType() {
        Field field = ReflectionSupport.findFieldOfType(
                CorrectTypeShadowChild.class,
                List.class,
                "passengers"
        );

        assertNotNull(field, "leaf-wins ordering should return the child's compatible shadow");
        assertSame(CorrectTypeShadowChild.class, field.getDeclaringClass());
        assertSame(List.class, field.getType());
    }

    @Test
    void findFieldOfType_reproducesOriginalBugWithDataWatcherObjectShadowing() throws Exception {
        // legacy findField reproduces the production ClassCastException trigger: the "ag" candidate on
        // StubEntityLiving matches a DataWatcherObject before the walk reaches StubEntity.passengers
        Field buggy = ReflectionSupport.findField(
                StubZombie.class,
                "passengers", "ag", "passengerList"
        );
        assertNotNull(buggy, "legacy findField should still return a field (the wrong one)");
        assertEquals("ag", buggy.getName(),
                "legacy findField returns the ag shadow from StubEntityLiving — proves the bug");
        assertSame(StubEntityLiving.class, buggy.getDeclaringClass());
        assertSame(StubDataWatcherObject.class, buggy.getType());

        // type-filtered lookup skips the DataWatcherObject and walks up to StubEntity.passengers
        Field correct = ReflectionSupport.findFieldOfType(
                StubZombie.class,
                List.class,
                "passengers", "ag", "passengerList"
        );
        assertNotNull(correct, "type-filtered lookup must find the List field on StubEntity");
        assertEquals("passengers", correct.getName());
        assertSame(StubEntity.class, correct.getDeclaringClass());
        assertSame(List.class, correct.getType());

        Object value = correct.get(new StubZombie());
        assertTrue(value instanceof ArrayList,
                "value read through the filtered field must be the real passengers List");
    }

    @Test
    void findFieldOfType_returnsNullWhenNoNameMatches() {
        Field field = ReflectionSupport.findFieldOfType(
                EmptyStub.class,
                List.class,
                "nonExistentField"
        );
        assertNull(field);
    }

    @Test
    void findFieldOfType_returnsNullWhenNameMatchesButTypeIncompatible() {
        Field field = ReflectionSupport.findFieldOfType(
                IncompatibleTypeOnly.class,
                List.class,
                "passengers"
        );
        assertNull(field, "a name-only match with wrong type must not be returned");
    }

    @Test
    void requireFieldOfType_throwsWhenNoMatch() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ReflectionSupport.requireFieldOfType(
                        EmptyStub.class,
                        List.class,
                        "foo", "bar"
                )
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains(List.class.getName()),
                "message should include the expected type FQN: " + message);
        assertTrue(message.contains(EmptyStub.class.getName()),
                "message should include the searched class FQN: " + message);
        assertTrue(message.contains("foo"), "message should include candidate 'foo': " + message);
        assertTrue(message.contains("bar"), "message should include candidate 'bar': " + message);
    }

    @Test
    void findFieldOfType_setsAccessibleOnReturnedField() {
        Field field = ReflectionSupport.findFieldOfType(
                PrivateListHolder.class,
                List.class,
                "passengers"
        );
        assertNotNull(field);

        // a private field requires setAccessible(true) before Field#get succeeds; a successful read here
        // proves the helper opened access without the test touching setAccessible itself
        PrivateListHolder instance = new PrivateListHolder();
        Object value = assertDoesNotThrow(() -> field.get(instance));
        assertTrue(value instanceof ArrayList);
    }

    @Test
    void findFieldOfType_walksFullSuperclassChain() {
        Field field = ReflectionSupport.findFieldOfType(
                ChainLeaf.class,
                List.class,
                "passengers"
        );
        assertNotNull(field);
        assertSame(ChainBase.class, field.getDeclaringClass(),
                "lookup must walk ChainLeaf -> ChainMiddle -> ChainBase");
    }

    @Test
    void findFieldOfType_secondCandidateNameWinsWhenFirstMissing() {
        Field field = ReflectionSupport.findFieldOfType(
                SecondCandidateHolder.class,
                List.class,
                "passengers", "au"
        );
        assertNotNull(field);
        assertEquals("au", field.getName());
        assertSame(SecondCandidateHolder.class, field.getDeclaringClass());
    }

    private static class WrongTypeShadowParent {
        public List<Object> passengers = new ArrayList<Object>();
    }

    private static class WrongTypeShadowChild extends WrongTypeShadowParent {
        public static final Object passengers = "not a list";
    }

    private static class CorrectTypeShadowParent {
        public List<Object> passengers = new ArrayList<Object>();
    }

    private static class CorrectTypeShadowChild extends CorrectTypeShadowParent {
        public List<Integer> passengers = new ArrayList<Integer>();
    }

    private static class StubDataWatcherObject {
    }

    private static class StubEntity {
        public List<Object> passengers = new ArrayList<Object>();
    }

    private static class StubEntityLiving extends StubEntity {
        public static final StubDataWatcherObject ag = new StubDataWatcherObject();
    }

    private static class StubZombie extends StubEntityLiving {
    }

    private static class EmptyStub {
    }

    private static class IncompatibleTypeOnly {
        public static final Object passengers = "not a list";
    }

    private static class PrivateListHolder {
        private List<Object> passengers = new ArrayList<Object>();
    }

    private static class ChainBase {
        public List<Object> passengers = new ArrayList<Object>();
    }

    private static class ChainMiddle extends ChainBase {
    }

    private static class ChainLeaf extends ChainMiddle {
    }

    private static class SecondCandidateHolder {
        public List<Object> au = new ArrayList<Object>();
    }
}
