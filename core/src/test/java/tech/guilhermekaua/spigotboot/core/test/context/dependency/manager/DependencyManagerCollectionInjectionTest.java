package tech.guilhermekaua.spigotboot.core.test.context.dependency.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyManagerCollectionInjectionTest {
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

    static class ServiceImpl2 implements Service {
        @Override
        public String getValue() {
            return "service2";
        }
    }

    static class ServiceImpl3 implements Service {
        @Override
        public String getValue() {
            return "service3";
        }
    }

    static class ListFieldInjected {
        @Inject
        List<Service> services;

        public List<Service> getServices() {
            return services;
        }
    }

    static class SetFieldInjected {
        @Inject
        Set<Service> services;

        public Set<Service> getServices() {
            return services;
        }
    }

    static class CollectionFieldInjected {
        @Inject
        Collection<Service> services;

        public Collection<Service> getServices() {
            return services;
        }
    }

    static class ListConstructorInjected {
        private final List<Service> services;

        public ListConstructorInjected(List<Service> services) {
            this.services = services;
        }

        public List<Service> getServices() {
            return services;
        }
    }

    static class SetConstructorInjected {
        private final Set<Service> services;

        public SetConstructorInjected(Set<Service> services) {
            this.services = services;
        }

        public Set<Service> getServices() {
            return services;
        }
    }

    static class ListSetterInjected {
        private List<Service> services;

        @Inject
        public void setServices(List<Service> services) {
            this.services = services;
        }

        public List<Service> getServices() {
            return services;
        }
    }

    @Test
    void testListFieldInjection() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl2.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl3.class, null, false);
        dependencyManager.registerDependency(ListFieldInjected.class, null, false, null, null);

        ListFieldInjected obj = dependencyManager.resolveDependency(ListFieldInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertEquals(3, obj.getServices().size());
        assertInstanceOf(List.class, obj.getServices());
        assertTrue(obj.getServices().stream().anyMatch(s -> "service".equals(s.getValue())));
        assertTrue(obj.getServices().stream().anyMatch(s -> "service2".equals(s.getValue())));
        assertTrue(obj.getServices().stream().anyMatch(s -> "service3".equals(s.getValue())));
    }

    @Test
    void testSetFieldInjection() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl2.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl3.class, null, false);
        dependencyManager.registerDependency(SetFieldInjected.class, null, false, null, null);

        SetFieldInjected obj = dependencyManager.resolveDependency(SetFieldInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertEquals(3, obj.getServices().size());
        assertInstanceOf(Set.class, obj.getServices());
        assertTrue(obj.getServices().stream().anyMatch(s -> "service".equals(s.getValue())));
        assertTrue(obj.getServices().stream().anyMatch(s -> "service2".equals(s.getValue())));
        assertTrue(obj.getServices().stream().anyMatch(s -> "service3".equals(s.getValue())));
    }

    @Test
    void testCollectionFieldInjection() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl2.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl3.class, null, false);
        dependencyManager.registerDependency(CollectionFieldInjected.class, null, false, null, null);

        CollectionFieldInjected obj = dependencyManager.resolveDependency(CollectionFieldInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertEquals(3, obj.getServices().size());
        assertInstanceOf(Collection.class, obj.getServices());
        assertTrue(obj.getServices().stream().anyMatch(s -> "service".equals(s.getValue())));
        assertTrue(obj.getServices().stream().anyMatch(s -> "service2".equals(s.getValue())));
        assertTrue(obj.getServices().stream().anyMatch(s -> "service3".equals(s.getValue())));
    }

    @Test
    void testListConstructorInjection() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl2.class, null, false);
        dependencyManager.registerDependency(ListConstructorInjected.class, null, false, null, null);

        ListConstructorInjected obj = dependencyManager.resolveDependency(ListConstructorInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertEquals(2, obj.getServices().size());
        assertInstanceOf(List.class, obj.getServices());
    }

    @Test
    void testSetConstructorInjection() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl2.class, null, false);
        dependencyManager.registerDependency(SetConstructorInjected.class, null, false, null, null);

        SetConstructorInjected obj = dependencyManager.resolveDependency(SetConstructorInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertEquals(2, obj.getServices().size());
        assertInstanceOf(Set.class, obj.getServices());
    }

    @Test
    void testListSetterInjection() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl2.class, null, false);
        dependencyManager.registerDependency(ListSetterInjected.class, null, false, null, null);

        ListSetterInjected obj = dependencyManager.resolveDependency(ListSetterInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertEquals(2, obj.getServices().size());
        assertInstanceOf(List.class, obj.getServices());
    }

    @Test
    void testListFieldInjectionViaInjectDependencies() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl2.class, null, false);
        ListFieldInjected obj = new ListFieldInjected();
        dependencyManager.injectDependencies(obj);

        assertNotNull(obj.services);
        assertEquals(2, obj.services.size());
        assertInstanceOf(List.class, obj.services);
    }

    @Test
    void testEmptyCollectionInjection() {
        dependencyManager.registerDependency(CollectionFieldInjected.class, null, false, null, null);

        CollectionFieldInjected obj = dependencyManager.resolveDependency(CollectionFieldInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertTrue(obj.getServices().isEmpty());
    }

    @Test
    void testCollectionInjectionIgnoresQualifiers() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(Service.class, ServiceImpl2.class, "qualifier1", false);
        dependencyManager.registerDependency(Service.class, ServiceImpl3.class, "qualifier2", false);
        dependencyManager.registerDependency(ListFieldInjected.class, null, false, null, null);

        ListFieldInjected obj = dependencyManager.resolveDependency(ListFieldInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertEquals(3, obj.getServices().size());
    }

    @Test
    void testCollectionInjectionWithSingleBean() {
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(ListFieldInjected.class, null, false, null, null);

        ListFieldInjected obj = dependencyManager.resolveDependency(ListFieldInjected.class, null);
        assertNotNull(obj);
        assertNotNull(obj.getServices());
        assertEquals(1, obj.getServices().size());
        assertEquals("service", obj.getServices().get(0).getValue());
    }
}

