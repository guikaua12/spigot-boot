package tech.guilhermekaua.spigotboot.core.test.context.dependency.manager;

import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyReloadCallback;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.exceptions.CircularDependencyException;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyManagerTest {
    private DependencyManager dependencyManager;

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
    }

    interface Service {
        String getValue();
    }

    static class ServiceImpl implements Service {
        @Override
        public String getValue() {
            return "service";
        }
    }

    static class ConstructorInjected {
        private final Service service;

        @Inject
        public ConstructorInjected(Service service) {
            this.service = service;
        }

        public Service getService() {
            return service;
        }
    }

    static class FieldInjected {
        @Inject
        Service service;

        public Service getService() {
            return service;
        }
    }

    static class SetterInjected {
        private Service service;

        @Inject
        public void setService(Service service) {
            this.service = service;
        }

        public Service getService() {
            return service;
        }
    }

    @Getter
    static class CircularA {
        @Inject
        private CircularB circularB;
    }

    @Getter
    static class CircularB {
        @Inject
        private CircularA circularA;
    }

    @Test
    void testRegisterAndResolve() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);

        Service service = dependencyManager.resolveDependency(Service.class, null);
        assertNotNull(service);
        assertEquals("service", service.getValue());
    }

    @Test
    void testSingletonBehavior() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);

        Service s1 = dependencyManager.resolveDependency(Service.class, null);
        Service s2 = dependencyManager.resolveDependency(Service.class, null);

        assertSame(s1, s2);
    }

    @Test
    void testRegisterInstance() {
        ServiceImpl impl = new ServiceImpl();
        dependencyManager.registerDependency(Service.class, impl, null, false);

        Service resolved = dependencyManager.resolveDependency(Service.class, null);
        assertSame(impl, resolved);
    }

    @Test
    void testRegisterAlreadyRegistered() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        });

        assertTrue(exception.getCause().getMessage().contains("already exists"));
    }

    @Test
    void testRegisterSameClassDifferentQualifier() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);

        assertDoesNotThrow(() -> dependencyManager.registerDependency(Service.class, ServiceImpl.class, "someQualifier", false));
    }

    @Test
    void testResolveWithTwoDependenciesAndQualifier() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, "someQualifier", false);

        Service service = assertDoesNotThrow(() -> dependencyManager.resolveDependency(Service.class, "someQualifier"));
        assertNotNull(service);
    }

    @Test
    void testResolveWithTwoDependenciesAndPrimary() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, true);
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, "someQualifier", false);

        Service service = assertDoesNotThrow(() -> dependencyManager.resolveDependency(Service.class, null));
        assertNotNull(service);
    }

    @Test
    void testNotResolveWithTwoDependenciesAndNoPrimary() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, "someQualifier", false);

        Exception exception = assertThrows(RuntimeException.class, () -> dependencyManager.resolveDependency(Service.class, null));
        assertTrue(exception.getCause().getMessage().contains("No primary dependency found for class"));
    }

    @Test
    void testRegisterInterfaceWithResolver() {
        ServiceImpl serviceToBeResolved = new ServiceImpl();
        dependencyManager.registerDependency(Service.class, null, false, (type) -> serviceToBeResolved);

        Service service = dependencyManager.resolveDependency(Service.class, null);
        assertNotNull(service);
        assertSame(serviceToBeResolved, service);
    }

    @Test
    void testRegisterInterfaceWithoutResolver() {
        Exception exception = assertThrows(RuntimeException.class, () -> dependencyManager.registerDependency(Service.class, null, false, null, null));
        assertTrue(exception.getCause().getMessage().contains("cannot register an interface without a resolver"));
    }

    @Test
    void testConstructorInjection() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(ConstructorInjected.class, null, false, null, null);

        ConstructorInjected obj = dependencyManager.resolveDependency(ConstructorInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getService());
        assertEquals("service", obj.getService().getValue());
    }

    @Test
    void testFieldInjectionViaResolve() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(FieldInjected.class, null, false, null, null);

        FieldInjected obj = dependencyManager.resolveDependency(FieldInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getService());
        assertEquals("service", obj.getService().getValue());
    }

    @Test
    void testFieldInjectionViaInjectDependencies() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        FieldInjected obj = new FieldInjected();
        dependencyManager.injectDependencies(obj);

        assertNotNull(obj.service);
        assertEquals("service", obj.service.getValue());
    }

    @Test
    void testSetterInjectionViaResolve() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(SetterInjected.class, null, false, null, null);

        SetterInjected obj = dependencyManager.resolveDependency(SetterInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getService());
        assertEquals("service", obj.getService().getValue());
    }

    @Test
    void testSetterInjectionViaInjectDependencies() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        SetterInjected obj = new SetterInjected();
        dependencyManager.injectDependencies(obj);

        assertNotNull(obj.getService());
        assertEquals("service", obj.getService().getValue());
    }

    @Test
    void testResolveUnregisteredTypeReturnsNull() {
        assertNull(dependencyManager.resolveDependency(Service.class, null));
    }

    @Test
    void testCircularDependency() {
        dependencyManager.registerDependency(CircularA.class, null, false, null, null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            dependencyManager.registerDependency(CircularB.class, null, false, null, null);
        });

        Throwable cause = exception.getCause();
        assertInstanceOf(CircularDependencyException.class, cause);
        assertEquals("Circular dependency detected: CircularB -> CircularA -> CircularB", cause.getMessage());
    }

    @Test
    void testReloadDependencies() throws Exception {
        DependencyReloadCallback callback = Mockito.mock(DependencyReloadCallback.class);
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false, null, callback);

        Service service = dependencyManager.resolveDependency(Service.class, null);

        assertNotNull(service);

        dependencyManager.reloadDependencies();

        Mockito.verify(callback, Mockito.times(1)).reload(service, dependencyManager);
    }

    @Test
    void testReloadWithNullCallback() {
        BeanDefinition definition = new BeanDefinition(Service.class, ServiceImpl.class, null, false, null, null);
        dependencyManager.getBeanDefinitionRegistry().register(Service.class, definition);
        dependencyManager.getBeanInstanceRegistry().put(definition, new ServiceImpl());

        assertDoesNotThrow(() -> dependencyManager.reloadDependencies());
    }

    @Test
    void testReloadWithNullInstance() throws Exception {
        DependencyReloadCallback callback = Mockito.mock(DependencyReloadCallback.class);

        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false, null, callback);

        dependencyManager.reloadDependencies();

        Mockito.verify(callback, Mockito.never()).reload(Mockito.any(), Mockito.eq(dependencyManager));
    }

    @Test
    void testReloadCallbackException() throws Exception {
        DependencyReloadCallback callback = Mockito.mock(DependencyReloadCallback.class);
        Mockito.doThrow(new RuntimeException("simulated exception"))
                .when(callback)
                .reload(Mockito.any(), Mockito.eq(dependencyManager));

        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false, null, callback);
        dependencyManager.resolveDependency(Service.class, null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            dependencyManager.reloadDependencies();
        });

        assertTrue(exception.getMessage().contains("Failed to reload dependency"));
        assertEquals("simulated exception", exception.getCause().getMessage());
    }

    @Test
    void testClearDependencies() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        assertEquals(1, dependencyManager.getBeanDefinitionRegistry().getRegisteredTypes().size());

        assertNotNull(dependencyManager.resolveDependency(Service.class, null));
        dependencyManager.clear();

        assertNull(dependencyManager.resolveDependency(Service.class, null));
        assertTrue(dependencyManager.getBeanDefinitionRegistry().getRegisteredTypes().isEmpty());
    }
}
