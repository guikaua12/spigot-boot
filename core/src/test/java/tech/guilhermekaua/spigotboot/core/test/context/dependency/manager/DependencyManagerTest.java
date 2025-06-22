package tech.guilhermekaua.spigotboot.core.test.context.dependency.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.exceptions.CircularDependencyException;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyManagerTest {

    private DependencyManager dependencyManager;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Plugin mockPlugin = MockBukkit.createMockPlugin();
        dependencyManager = new DependencyManager(mockPlugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
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
    void testRegisterInterfaceWithoutResolver() {
        Exception exception = assertThrows(RuntimeException.class, () -> dependencyManager.registerDependency(Service.class, null, false, null));
        assertTrue(exception.getCause().getMessage().contains("cannot register an interface without a resolver"));
    }

    @Test
    void testConstructorInjection() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(ConstructorInjected.class, null, false, null);

        ConstructorInjected obj = dependencyManager.resolveDependency(ConstructorInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getService());
        assertEquals("service", obj.getService().getValue());
    }

    @Test
    void testFieldInjectionViaResolve() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(FieldInjected.class, null, false, null);

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
        dependencyManager.registerDependency(SetterInjected.class, null, false, null);

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
        dependencyManager.registerDependency(CircularA.class, null, false, null);
        dependencyManager.registerDependency(CircularB.class, null, false, null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            dependencyManager.resolveDependency(CircularA.class, null);
        });

        Throwable cause = exception.getCause();
        while (cause != null && !(cause instanceof CircularDependencyException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause);
        assertTrue(cause.getMessage().contains("Circular dependency detected"));
    }
}
