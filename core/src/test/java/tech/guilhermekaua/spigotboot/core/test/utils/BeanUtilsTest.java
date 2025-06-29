package tech.guilhermekaua.spigotboot.core.test.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnReload;
import tech.guilhermekaua.spigotboot.core.context.annotations.Primary;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

@ExtendWith(MockitoExtension.class)
public class BeanUtilsTest {

    @Qualifier("testQualifier")
    static class QualifierTestClass {
        @Qualifier("testFieldQualifier")
        private Object field;

        public void method(@Qualifier("testParameterQualifier") Object param) {
        }
    }

    static class QualifierTestClassNoAnnotation {
        private Object field;

        public void method(Object param) {
        }
    }

    @Primary
    static class PrimaryTestClass {
        @Primary
        public void primaryMethod() {
        }
    }

    static class NotPrimaryTestClass {
        public void nonPrimaryMethod() {
        }
    }

    static class ServiceImpl {
    }

    static class OnReloadTestClass {
        @OnReload
        public void onReloadMethod(ServiceImpl dependency) {
        }

        @OnReload
        public void onReloadMethodQualifier(@Qualifier("service2") ServiceImpl dependency) {
        }

        public void nonReloadMethod(ServiceImpl dependency) {
        }
    }

    @Test
    public void testGetQualifier() throws NoSuchFieldException, NoSuchMethodException {
        String qualifier = BeanUtils.getQualifier(QualifierTestClass.class);
        String fieldQualifier = BeanUtils.getQualifier(QualifierTestClass.class.getDeclaredField("field"));
        String methodQualifier = BeanUtils.getQualifier(
                QualifierTestClass.class.getDeclaredMethod("method", Object.class).getParameters()[0]
        );

        Assertions.assertEquals("testQualifier", qualifier);
        Assertions.assertEquals("testFieldQualifier", fieldQualifier);
        Assertions.assertEquals("testParameterQualifier", methodQualifier);
    }

    @Test
    public void testGetQualifierNoAnnotation() throws NoSuchFieldException, NoSuchMethodException {
        String qualifier = BeanUtils.getQualifier(QualifierTestClassNoAnnotation.class);
        String fieldQualifier = BeanUtils.getQualifier(QualifierTestClassNoAnnotation.class.getDeclaredField("field"));
        String methodQualifier = BeanUtils.getQualifier(
                QualifierTestClassNoAnnotation.class.getDeclaredMethod("method", Object.class).getParameters()[0]
        );

        Assertions.assertNull(qualifier);
        Assertions.assertNull(fieldQualifier);
        Assertions.assertNull(methodQualifier);
    }

    @Test
    public void testGetQualifierNullElement() {
        Assertions.assertThrows(Exception.class, () -> BeanUtils.getQualifier(null));
    }

    @Test
    public void testGetIsPrimary() throws NoSuchMethodException {
        boolean isPrimary = BeanUtils.getIsPrimary(PrimaryTestClass.class);
        boolean isFieldPrimary = BeanUtils.getIsPrimary(
                PrimaryTestClass.class.getDeclaredMethod("primaryMethod")
        );

        Assertions.assertTrue(isPrimary);
        Assertions.assertTrue(isFieldPrimary);
    }

    @Test
    public void testGetIsNotPrimary() throws NoSuchMethodException {
        boolean isPrimary = BeanUtils.getIsPrimary(NotPrimaryTestClass.class);
        boolean isFieldPrimary = BeanUtils.getIsPrimary(
                NotPrimaryTestClass.class.getDeclaredMethod("nonPrimaryMethod")
        );

        Assertions.assertFalse(isPrimary);
        Assertions.assertFalse(isFieldPrimary);
    }

    @Test
    public void testGetIsPrimaryNullElement() {
        Assertions.assertThrows(Exception.class, () -> BeanUtils.getIsPrimary(null));
    }

    @Test
    public void testCreateDependencyReloadCallback() throws Exception {
        DependencyManager dependencyManager = Mockito.mock(DependencyManager.class);
        OnReloadTestClass instance = Mockito.mock(OnReloadTestClass.class);

        ServiceImpl service = new ServiceImpl();
        Mockito.doReturn(service).when(dependencyManager).resolveDependency(Mockito.eq(ServiceImpl.class), Mockito.any());

        DependencyReloadCallback callback = BeanUtils.createDependencyReloadCallback(OnReloadTestClass.class);
        Assertions.assertNotNull(callback);

        callback.reload(instance, dependencyManager);

        Mockito.verify(instance, Mockito.times(1)).onReloadMethod(Mockito.eq(service));
    }

    @Test
    public void testCreateDependencyReloadCallbackWithQualifierParam() throws Exception {
        DependencyManager dependencyManager = Mockito.mock(DependencyManager.class);
        OnReloadTestClass instance = Mockito.mock(OnReloadTestClass.class);

        ServiceImpl service1 = Mockito.mock(ServiceImpl.class);
        ServiceImpl service2 = Mockito.mock(ServiceImpl.class);

        Mockito.doReturn(service1).when(dependencyManager).resolveDependency(Mockito.eq(ServiceImpl.class), Mockito.eq(null));
        Mockito.doReturn(service2).when(dependencyManager).resolveDependency(Mockito.eq(ServiceImpl.class), Mockito.eq("service2"));

        DependencyReloadCallback callback = BeanUtils.createDependencyReloadCallback(OnReloadTestClass.class);
        Assertions.assertNotNull(callback);

        callback.reload(instance, dependencyManager);

        Mockito.verify(instance, Mockito.times(1)).onReloadMethod(Mockito.eq(service1));
        Mockito.verify(instance, Mockito.times(1)).onReloadMethodQualifier(Mockito.eq(service2));
        Mockito.verify(instance, Mockito.never()).nonReloadMethod(Mockito.any());
    }

    @Test
    public void testCreateDependencyReloadCallbackNullElement() {
        Assertions.assertThrows(Exception.class, () -> BeanUtils.createDependencyReloadCallback(null));
    }
}
