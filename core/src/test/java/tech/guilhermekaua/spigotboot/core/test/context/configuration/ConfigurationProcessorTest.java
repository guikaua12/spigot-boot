package tech.guilhermekaua.spigotboot.core.test.context.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.annotations.Primary;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.configuration.proxy.ConfigurationClassProxy;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationProcessorTest {
    private DependencyManager dependencyManager;
    private ConfigurationProcessor processor;

    private static final AtomicInteger testServiceCreationCount = new AtomicInteger(0);
    private static final AtomicInteger anotherServiceCreationCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
        processor = new ConfigurationProcessor();
        testServiceCreationCount.set(0);
        anotherServiceCreationCount.set(0);
    }

    public interface TestService {
        String getValue();
    }

    public static class TestServiceImpl implements TestService {
        private final String value;

        public TestServiceImpl(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    public static class AnotherService {
        private final TestService testService;

        public AnotherService(TestService testService) {
            this.testService = testService;
        }

        public TestService getTestService() {
            return testService;
        }
    }

    @Configuration
    public static class TestConfiguration {
        @Bean
        @Primary
        public TestService testService() {
            testServiceCreationCount.incrementAndGet();
            return new TestServiceImpl("test-value");
        }

        @Bean
        public AnotherService anotherService() {
            anotherServiceCreationCount.incrementAndGet();
            return new AnotherService(testService());
        }

        public String nonBeanMethod() {
            return "non-bean-result";
        }
    }

    // Configuration with qualified beans
    @Configuration
    public static class QualifiedConfiguration {
        @Bean
        @Qualifier("primary")
        @Primary
        public TestService primaryService() {
            return new TestServiceImpl("primary");
        }

        @Bean
        @Qualifier("secondary")
        public TestService secondaryService() {
            return new TestServiceImpl("secondary");
        }
    }

    public static class CtorDependency {
        private final String value;

        public CtorDependency(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class CtorBean {
        private final String value;

        public CtorBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Configuration
    public static class CtorInjectedConfiguration {
        private final CtorDependency dep;

        public CtorInjectedConfiguration(CtorDependency dep) {
            this.dep = dep;
        }

        @Bean
        public CtorBean ctorBean() {
            return new CtorBean(dep.getValue());
        }
    }

    @Test
    void testBeanMethodRegisteredAsLazyDefinition() {
        processor.processClass(TestConfiguration.class, dependencyManager);

        List<BeanDefinition> definitions = dependencyManager.getBeanDefinitionRegistry()
                .getDefinitions(TestService.class);
        assertFalse(definitions.isEmpty(), "Bean definition should be registered");

        assertEquals(0, testServiceCreationCount.get(), "Bean should not be instantiated during registration");
    }

    @Test
    void testBeanOnlyCreatedOnFirstAccess() {
        processor.processClass(TestConfiguration.class, dependencyManager);

        assertEquals(0, testServiceCreationCount.get(), "Bean should not be created during processing");

        TestService service = dependencyManager.resolveDependency(TestService.class, null);

        assertEquals(1, testServiceCreationCount.get(), "Bean should be created on first access");
        assertNotNull(service, "Resolved service should not be null");
        assertEquals("test-value", service.getValue(), "Service should return correct value");
    }

    @Test
    void testBeanCachedAfterFirstAccess() {
        processor.processClass(TestConfiguration.class, dependencyManager);

        TestService service1 = dependencyManager.resolveDependency(TestService.class, null);
        TestService service2 = dependencyManager.resolveDependency(TestService.class, null);

        assertEquals(1, testServiceCreationCount.get(), "Bean should only be created once");
        assertSame(service1, service2, "Both resolutions should return the same instance");
    }

    @Test
    void testConfigProxyRoutesBeanMethodsToDependencyManager() throws Exception {
        processor.processClass(TestConfiguration.class, dependencyManager);

        TestConfiguration configProxy = dependencyManager.resolveDependency(TestConfiguration.class, null);
        assertNotNull(configProxy, "Config proxy should not be null");

        assertTrue(configProxy.getClass().getName().contains("$"), "Config should be a proxy class");

        TestService service1 = configProxy.testService();
        TestService service2 = configProxy.testService();

        assertSame(service1, service2, "Proxy bean method calls should return cached instances");

        assertEquals(1, testServiceCreationCount.get(),
                "Bean should only be instantiated once even when called via proxy");
    }

    @Test
    void testConfigProxyPassesNonBeanMethodsToRealObject() {
        processor.processClass(TestConfiguration.class, dependencyManager);

        TestConfiguration configProxy = dependencyManager.resolveDependency(TestConfiguration.class, null);

        String result = configProxy.nonBeanMethod();

        assertEquals("non-bean-result", result, "Non-bean method should be delegated to real object");
    }

    @Test
    void testBeanMethodWithDependencies() {
        processor.processClass(TestConfiguration.class, dependencyManager);

        AnotherService anotherService = dependencyManager.resolveDependency(AnotherService.class, null);

        assertEquals(1, testServiceCreationCount.get(), "TestService should be created");
        assertEquals(1, anotherServiceCreationCount.get(), "AnotherService should be created");

        assertNotNull(anotherService, "AnotherService should not be null");
        assertNotNull(anotherService.getTestService(), "Injected TestService should not be null");
        assertEquals("test-value", anotherService.getTestService().getValue(),
                "Injected TestService should have correct value");
    }

    @Test
    void testQualifiedBeans() {
        processor.processClass(QualifiedConfiguration.class, dependencyManager);

        TestService primary = dependencyManager.resolveDependency(TestService.class, "primary");
        TestService secondary = dependencyManager.resolveDependency(TestService.class, "secondary");

        assertNotNull(primary, "Primary service should not be null");
        assertNotNull(secondary, "Secondary service should not be null");
        assertEquals("primary", primary.getValue());
        assertEquals("secondary", secondary.getValue());
        assertNotSame(primary, secondary, "Different qualifiers should yield different instances");
    }

    @Test
    void testConfigurationClassProxyDirectCreation() throws Exception {
        Set<Method> beanMethods = new HashSet<>();
        beanMethods.add(TestConfiguration.class.getMethod("testService"));

        dependencyManager.registerDependency(
                TestService.class,
                null,
                true,
                (type) -> null);

        TestConfiguration proxy = ConfigurationClassProxy.createProxy(
                TestConfiguration.class,
                beanMethods,
                dependencyManager);

        TestService service1 = proxy.testService();
        TestService service2 = proxy.testService();

        assertSame(service1, service2, "Proxy should return cached bean instances");
    }

    @Test
    void testConfigurationProxyConstructorInjection() {
        dependencyManager.registerDependency(new CtorDependency("ctor-value"), null, false);

        processor.processClass(CtorInjectedConfiguration.class, dependencyManager);

        CtorBean bean = dependencyManager.resolveDependency(CtorBean.class, null);
        assertNotNull(bean, "CtorBean should not be null");
        assertEquals("ctor-value", bean.getValue(), "CtorBean should be created using constructor-injected dependency");
    }

    @Test
    void testInterBeanMethodCallUsesSameInstance() {
        processor.processClass(TestConfiguration.class, dependencyManager);

        AnotherService anotherService = dependencyManager.resolveDependency(AnotherService.class, null);

        TestService directService = dependencyManager.resolveDependency(TestService.class, null);

        assertSame(anotherService.getTestService(), directService,
                "Inter-bean method call should return the same singleton instance as direct resolution");

        assertEquals(1, testServiceCreationCount.get(),
                "TestService should only be instantiated once even when called from another @Bean method");
    }
}
