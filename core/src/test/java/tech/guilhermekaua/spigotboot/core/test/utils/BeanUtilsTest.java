package tech.guilhermekaua.spigotboot.core.test.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnReload;
import tech.guilhermekaua.spigotboot.core.context.annotations.Primary;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;
import tech.guilhermekaua.spigotboot.core.context.dependency.Dependency;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.exceptions.CircularDependencyException;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BeanUtilsTest {

    private Map<Class<?>, List<Dependency>> dependencyMap;

    @BeforeEach
    void setUp() {
        dependencyMap = new HashMap<>();
    }

    static class SimpleClass {
        @Qualifier("test")
        private String field;
    }

    @Primary
    static class PrimaryClass {
    }

    static class NonPrimaryClass {
    }

    static class WithReloadMethod {
        @OnReload
        public void onReload(String dependency) {
        }

        @OnReload
        public void anotherReload(Integer dependency, String anotherDep) {
        }

        public void normalMethod() {
        }
    }

    static class CircularA {
        @Inject
        private CircularB circularB;
    }

    static class CircularB {
        @Inject
        private CircularA circularA;
    }

    static class CircularC {
        @Inject
        private CircularD circularD;
    }

    static class CircularD {
        @Inject
        private CircularE circularE;
    }

    static class CircularE {
        @Inject
        private CircularC circularC;
    }

    static class NonCircularA {
        @Inject
        private NonCircularB nonCircularB;
    }

    static class NonCircularB {
        @Inject
        private String someString;
    }

    static class ConstructorInjection {
        @Inject
        public ConstructorInjection(String dependency) {
        }
    }

    static class FieldInjection {
        @Inject
        private String dependency;
    }

    static class SetterInjection {
        @Inject
        public void setDependency(String dependency) {
        }
    }

    static class MixedInjection {
        @Inject
        private String fieldDep;

        @Inject
        public MixedInjection(Integer constructorDep) {
        }

        @Inject
        public void setSetterDep(Double setterDep) {
        }
    }

    static class MultipleConstructors {
        public MultipleConstructors() {
        }

        public MultipleConstructors(String param) {
        }
    }

    static class SingleConstructor {
        public SingleConstructor(String param) {
        }
    }

    static class SelfDependent {
        @Inject
        private SelfDependent self;
    }

    static class Mixed1 {
        @Inject
        private Mixed2 field;

        @Inject
        public Mixed1(Mixed3 constructor) {
        }

        @Inject
        public void setSetter(Mixed4 setter) {
        }
    }

    static class Mixed2 {
        @Inject
        public Mixed2(Mixed1 dependency) {
        }
    }

    static class Mixed3 {
    }

    static class Mixed4 {
    }

    interface TestInterface {
    }

    @Test
    void testGetQualifier_WithQualifierAnnotation() throws NoSuchFieldException {
        Field field = SimpleClass.class.getDeclaredField("field");
        String qualifier = BeanUtils.getQualifier(field);
        assertEquals("test", qualifier);
    }

    @Test
    void testGetQualifier_WithoutQualifierAnnotation() {
        AnnotatedElement element = NonPrimaryClass.class;
        String qualifier = BeanUtils.getQualifier(element);
        assertNull(qualifier);
    }

    @Test
    void testGetQualifier_NullElement() {
        assertThrows(NullPointerException.class, () -> BeanUtils.getQualifier(null));
    }

    @Test
    void testGetIsPrimary_WithPrimaryAnnotation() {
        boolean isPrimary = BeanUtils.getIsPrimary(PrimaryClass.class);
        assertTrue(isPrimary);
    }

    @Test
    void testGetIsPrimary_WithoutPrimaryAnnotation() {
        boolean isPrimary = BeanUtils.getIsPrimary(NonPrimaryClass.class);
        assertFalse(isPrimary);
    }

    @Test
    void testGetIsPrimary_NullElement() {
        assertThrows(NullPointerException.class, () -> BeanUtils.getIsPrimary(null));
    }

    @Test
    void testCreateDependencyReloadCallback() {
        DependencyManager mockDependencyManager = mock(DependencyManager.class);
        when(mockDependencyManager.resolveDependency(String.class, null)).thenReturn("test");
        when(mockDependencyManager.resolveDependency(Integer.class, null)).thenReturn(42);

        DependencyReloadCallback callback = BeanUtils.createDependencyReloadCallback(WithReloadMethod.class);
        WithReloadMethod instance = new WithReloadMethod();

        assertDoesNotThrow(() -> callback.reload(instance, mockDependencyManager));

        verify(mockDependencyManager, times(2)).resolveDependency(String.class, null);
        verify(mockDependencyManager, times(1)).resolveDependency(Integer.class, null);
    }

    @Test
    void testCreateDependencyReloadCallback_NullClass() {
        assertThrows(NullPointerException.class, () -> BeanUtils.createDependencyReloadCallback(null));
    }

    @Test
    void testCreateDependencyReloadCallback_NoReloadMethods() {
        DependencyManager mockDependencyManager = mock(DependencyManager.class);
        DependencyReloadCallback callback = BeanUtils.createDependencyReloadCallback(NonPrimaryClass.class);
        NonPrimaryClass instance = new NonPrimaryClass();

        assertDoesNotThrow(() -> callback.reload(instance, mockDependencyManager));

        verifyNoInteractions(mockDependencyManager);
    }

    @Test
    void testDetectCircularDependencies_SimpleCircular() {
        dependencyMap.put(CircularA.class, List.of(
                new Dependency(CircularA.class, null, false, null, null, null)
        ));

        CircularDependencyException exception = assertThrows(CircularDependencyException.class, () ->
                BeanUtils.detectCircularDependencies(CircularB.class, dependencyMap)
        );
        assertEquals("Circular dependency detected: CircularB -> CircularA -> CircularB", exception.getMessage());
    }

    @Test
    void testDetectCircularDependencies_ComplexCircular() {
        dependencyMap.put(CircularC.class, List.of(
                new Dependency(CircularC.class, null, false, null, null, null)
        ));
        dependencyMap.put(CircularD.class, List.of(
                new Dependency(CircularD.class, null, false, null, null, null)
        ));

        // should detect circular dependency (E -> C -> D -> E)
        CircularDependencyException exception = assertThrows(CircularDependencyException.class, () ->
                BeanUtils.detectCircularDependencies(CircularE.class, dependencyMap)
        );
        assertEquals("Circular dependency detected: CircularE -> CircularC -> CircularD -> CircularE", exception.getMessage());
    }

    @Test
    void testDetectCircularDependencies_NonCircular() {
        dependencyMap.put(NonCircularB.class, List.of(
                new Dependency(NonCircularB.class, null, false, null, null, null)
        ));
        dependencyMap.put(String.class, List.of(
                new Dependency(String.class, null, false, null, null, null)
        ));

        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(NonCircularA.class, dependencyMap)
        );
    }

    @Test
    void testDetectCircularDependencies_EmptyDependencyMap() {
        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(CircularA.class, dependencyMap)
        );
    }

    @Test
    void testDetectCircularDependencies_NullParameters() {
        assertThrows(NullPointerException.class, () ->
                BeanUtils.detectCircularDependencies(null, dependencyMap)
        );

        assertThrows(NullPointerException.class, () ->
                BeanUtils.detectCircularDependencies(CircularA.class, null)
        );
    }

    @Test
    void testDetectCircularDependencies_SelfDependency() {
        dependencyMap.put(SelfDependent.class, List.of(
                new Dependency(SelfDependent.class, null, false, null, null, null)
        ));

        assertThrows(CircularDependencyException.class, () ->
                BeanUtils.detectCircularDependencies(SelfDependent.class, dependencyMap)
        );
    }

    @Test
    void testDetectCircularDependencies_MixedInjectionTypes() {
        dependencyMap.put(Mixed1.class, List.of(
                new Dependency(Mixed1.class, null, false, null, null, null)
        ));
        dependencyMap.put(Mixed3.class, List.of(
                new Dependency(Mixed3.class, null, false, null, null, null)
        ));
        dependencyMap.put(Mixed4.class, List.of(
                new Dependency(Mixed4.class, null, false, null, null, null)
        ));

        // should detect circular dependency through field injection
        assertThrows(CircularDependencyException.class, () ->
                BeanUtils.detectCircularDependencies(Mixed2.class, dependencyMap)
        );
    }

    @Test
    void testDetectCircularDependencies_InterfaceClass() {
        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(TestInterface.class, dependencyMap)
        );
    }

    @Test
    void testCircularDependencyMessage() {
        dependencyMap.put(CircularA.class, List.of(
                new Dependency(CircularA.class, null, false, null, null, null)
        ));

        CircularDependencyException exception = assertThrows(CircularDependencyException.class, () ->
                BeanUtils.detectCircularDependencies(CircularB.class, dependencyMap)
        );

        assertEquals("Circular dependency detected: CircularB -> CircularA -> CircularB", exception.getMessage());
    }

    @Test
    void testGetAllDependencies_ConstructorInjection() {
        dependencyMap.put(String.class, List.of(
                new Dependency(String.class, null, false, null, null, null)
        ));

        // should not throw since String doesn't depend on ConstructorInjection
        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(ConstructorInjection.class, dependencyMap)
        );
    }

    @Test
    void testGetAllDependencies_FieldInjection() {
        dependencyMap.put(String.class, List.of(
                new Dependency(String.class, null, false, null, null, null)
        ));

        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(FieldInjection.class, dependencyMap)
        );
    }

    @Test
    void testGetAllDependencies_SetterInjection() {
        dependencyMap.put(String.class, List.of(
                new Dependency(String.class, null, false, null, null, null)
        ));

        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(SetterInjection.class, dependencyMap)
        );
    }

    @Test
    void testGetAllDependencies_MixedInjection() {
        dependencyMap.put(String.class, List.of(
                new Dependency(String.class, null, false, null, null, null)
        ));
        dependencyMap.put(Integer.class, List.of(
                new Dependency(Integer.class, null, false, null, null, null)
        ));
        dependencyMap.put(Double.class, List.of(
                new Dependency(Double.class, null, false, null, null, null)
        ));

        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(MixedInjection.class, dependencyMap)
        );
    }

    @Test
    void testFindInjectConstructor_MultipleConstructors() {
        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(MultipleConstructors.class, dependencyMap)
        );
    }

    @Test
    void testFindInjectConstructor_SingleConstructor() {
        dependencyMap.put(String.class, List.of(
                new Dependency(String.class, null, false, null, null, null)
        ));

        assertDoesNotThrow(() ->
                BeanUtils.detectCircularDependencies(SingleConstructor.class, dependencyMap)
        );
    }
}
