package tech.guilhermekaua.spigotboot.core.test.context.dependency.injector;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CustomInjectorTest {
    private DependencyManager dependencyManager;

    @BeforeEach
    void setUp() {
        MethodHandlerRegistry.clear();
        dependencyManager = new DependencyManager();
    }

    @AfterEach
    void tearDown() {
        MethodHandlerRegistry.clear();
    }

    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface CustomValue {
        String value();
    }

    static class CustomValueInjector implements CustomInjector {
        private final String valueToInject;

        public CustomValueInjector(String valueToInject) {
            this.valueToInject = valueToInject;
        }

        @Override
        public boolean supports(@NotNull InjectionPoint injectionPoint) {
            return injectionPoint.getAnnotatedElement().isAnnotationPresent(CustomValue.class);
        }

        @Override
        public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
            CustomValue annotation = injectionPoint.getAnnotatedElement().getAnnotation(CustomValue.class);
            if (annotation != null) {
                return InjectionResult.handled(valueToInject + ":" + annotation.value());
            }
            return InjectionResult.notHandled();
        }
    }

    static class FieldInjectedBean {
        @Inject
        @CustomValue("field-key")
        String customField;

        public String getCustomField() {
            return customField;
        }
    }

    static class FieldInjectedWithoutInjectBean {
        @CustomValue("field-no-inject")
        String customField;

        String plainField;

        public String getCustomField() {
            return customField;
        }

        public String getPlainField() {
            return plainField;
        }
    }

    static class ConstructorInjectedBean {
        private final String customValue;

        public ConstructorInjectedBean(@CustomValue("ctor-key") String customValue) {
            this.customValue = customValue;
        }

        public String getCustomValue() {
            return customValue;
        }
    }

    static class SetterInjectedBean {
        private String customValue;

        @Inject
        @CustomValue("setter-key")
        public void setCustomValue(String customValue) {
            this.customValue = customValue;
        }

        public String getCustomValue() {
            return customValue;
        }
    }

    static class SetterInjectedWithoutInjectBean {
        private String customValue;
        private String plainValue;

        @CustomValue("setter-no-inject")
        public void setCustomValue(String customValue) {
            this.customValue = customValue;
        }

        public void setPlainValue(String plainValue) {
            this.plainValue = plainValue;
        }

        public String getCustomValue() {
            return customValue;
        }

        public String getPlainValue() {
            return plainValue;
        }
    }

    interface Service {
        String getValue();
    }

    static class ServiceImpl implements Service {
        @Override
        public String getValue() {
            return "service-value";
        }
    }

    static class MixedInjectionBean {
        @Inject
        @CustomValue("mixed-custom")
        String customField;

        @Inject
        Service regularService;

        public String getCustomField() {
            return customField;
        }

        public Service getRegularService() {
            return regularService;
        }
    }

    @Test
    void testCustomInjectorRegistration() {
        CustomValueInjector injector = new CustomValueInjector("test");

        dependencyManager.registerInjector(injector);

        assertEquals(1, dependencyManager.getCustomInjectorRegistry().size());
    }

    @Test
    void testCustomInjectorFieldInjection() {
        dependencyManager.registerInjector(new CustomValueInjector("injected"));
        dependencyManager.registerDependency(FieldInjectedBean.class, null, false, null, null);

        FieldInjectedBean bean = dependencyManager.resolveDependency(FieldInjectedBean.class, null);

        assertNotNull(bean);
        assertEquals("injected:field-key", bean.getCustomField());
    }

    @Test
    void testCustomInjectorConstructorInjection() {
        dependencyManager.registerInjector(new CustomValueInjector("ctor-injected"));
        dependencyManager.registerDependency(ConstructorInjectedBean.class, null, false, null, null);

        ConstructorInjectedBean bean = dependencyManager.resolveDependency(ConstructorInjectedBean.class, null);

        assertNotNull(bean);
        assertEquals("ctor-injected:ctor-key", bean.getCustomValue());
    }

    @Test
    void testCustomInjectorSetterInjection() {
        dependencyManager.registerInjector(new CustomValueInjector("setter-injected"));
        dependencyManager.registerDependency(SetterInjectedBean.class, null, false, null, null);

        SetterInjectedBean bean = dependencyManager.resolveDependency(SetterInjectedBean.class, null);

        assertNotNull(bean);
        assertEquals("setter-injected:setter-key", bean.getCustomValue());
    }

    @Test
    void testFieldInjectionWithoutInjectAnnotation() {
        dependencyManager.registerInjector(new CustomValueInjector("no-inject"));
        dependencyManager.registerDependency(FieldInjectedWithoutInjectBean.class, null, false, null, null);

        FieldInjectedWithoutInjectBean bean = dependencyManager.resolveDependency(FieldInjectedWithoutInjectBean.class, null);

        assertNotNull(bean);
        assertEquals("no-inject:field-no-inject", bean.getCustomField());
        assertNull(bean.getPlainField());
    }

    @Test
    void testSetterInjectionWithoutInjectAnnotation() {
        dependencyManager.registerInjector(new CustomValueInjector("no-inject-setter"));
        dependencyManager.registerDependency(SetterInjectedWithoutInjectBean.class, null, false, null, null);

        SetterInjectedWithoutInjectBean bean = dependencyManager.resolveDependency(SetterInjectedWithoutInjectBean.class, null);

        assertNotNull(bean);
        assertEquals("no-inject-setter:setter-no-inject", bean.getCustomValue());
        assertNull(bean.getPlainValue());
    }

    @Test
    void testCustomInjectorWithFallbackToDefaultResolution() {
        dependencyManager.registerInjector(new CustomValueInjector("custom"));
        dependencyManager.registerDependency(Service.class, ServiceImpl.class, null, false);
        dependencyManager.registerDependency(MixedInjectionBean.class, null, false, null, null);

        MixedInjectionBean bean = dependencyManager.resolveDependency(MixedInjectionBean.class, null);

        assertNotNull(bean);
        assertEquals("custom:mixed-custom", bean.getCustomField());
        assertNotNull(bean.getRegularService());
        assertEquals("service-value", bean.getRegularService().getValue());
    }

    @Test
    void testCustomInjectorPrecedence() {
        dependencyManager.registerInjector(new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint injectionPoint) {
                return injectionPoint.getAnnotatedElement().isAnnotationPresent(CustomValue.class);
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
                return InjectionResult.handled("low-priority");
            }

            @Override
            public int getOrder() {
                return 10;
            }
        });

        dependencyManager.registerInjector(new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint injectionPoint) {
                return injectionPoint.getAnnotatedElement().isAnnotationPresent(CustomValue.class);
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
                return InjectionResult.handled("high-priority");
            }

            @Override
            public int getOrder() {
                return -10;
            }
        });

        dependencyManager.registerDependency(FieldInjectedBean.class, null, false, null, null);

        FieldInjectedBean bean = dependencyManager.resolveDependency(FieldInjectedBean.class, null);

        assertNotNull(bean);
        assertEquals("high-priority", bean.getCustomField());
    }

    @Test
    void testCustomInjectorNotHandledFallsThrough() {
        dependencyManager.registerInjector(new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint injectionPoint) {
                return true;
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
                return InjectionResult.notHandled();
            }
        });

        dependencyManager.registerInjector(new CustomValueInjector("fallthrough"));

        dependencyManager.registerDependency(FieldInjectedBean.class, null, false, null, null);

        FieldInjectedBean bean = dependencyManager.resolveDependency(FieldInjectedBean.class, null);

        assertNotNull(bean);
        assertEquals("fallthrough:field-key", bean.getCustomField());
    }

    @Test
    void testCustomInjectorCanInjectNull() {
        dependencyManager.registerInjector(new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint injectionPoint) {
                return injectionPoint.getAnnotatedElement().isAnnotationPresent(CustomValue.class);
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
                return InjectionResult.handled(null); // intentionally inject null
            }
        });

        dependencyManager.registerDependency(FieldInjectedBean.class, null, false, null, null);

        FieldInjectedBean bean = dependencyManager.resolveDependency(FieldInjectedBean.class, null);

        assertNotNull(bean);
        assertNull(bean.getCustomField());
    }

    @Test
    void testInjectionPointFromField() throws NoSuchFieldException {
        InjectionPoint point = InjectionPoint.fromField(FieldInjectedBean.class.getDeclaredField("customField"));

        assertEquals(String.class, point.getType());
        assertNull(point.getQualifier());
        assertNotNull(point.getAnnotatedElement());
        assertTrue(point.getAnnotatedElement().isAnnotationPresent(CustomValue.class));
    }

    @Test
    void testInjectionPointFromParameter() throws NoSuchMethodException {
        Parameter param = ConstructorInjectedBean.class
                .getConstructor(String.class)
                .getParameters()[0];

        InjectionPoint point = InjectionPoint.fromParameter(param);

        assertEquals(String.class, point.getType());
        assertNull(point.getQualifier());
        assertNotNull(point.getAnnotatedElement());
    }

    @Test
    void testInjectionPointWithQualifier() throws NoSuchFieldException {
        class QualifiedBean {
            @Inject
            @Qualifier("myQualifier")
            String qualifiedField;
        }

        InjectionPoint point = InjectionPoint.fromField(QualifiedBean.class.getDeclaredField("qualifiedField"));

        assertEquals("myQualifier", point.getQualifier());
    }

    @Test
    void testInjectionResultNotHandled() {
        InjectionResult result = InjectionResult.notHandled();

        assertFalse(result.isHandled());
        assertThrows(IllegalStateException.class, result::getValue);
    }

    @Test
    void testInjectionResultHandledWithValue() {
        InjectionResult result = InjectionResult.handled("test-value");

        assertTrue(result.isHandled());
        assertEquals("test-value", result.getValue());
    }

    @Test
    void testInjectionResultHandledWithNull() {
        InjectionResult result = InjectionResult.handled(null);

        assertTrue(result.isHandled());
        assertNull(result.getValue());
    }

    @Test
    void testCustomInjectorRegistryOrder() {
        CustomInjectorRegistry registry = dependencyManager.getCustomInjectorRegistry();

        CustomInjector lowPriority = new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint ip) {
                return false;
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint ip) {
                return InjectionResult.notHandled();
            }

            @Override
            public int getOrder() {
                return 100;
            }
        };

        CustomInjector highPriority = new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint ip) {
                return false;
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint ip) {
                return InjectionResult.notHandled();
            }

            @Override
            public int getOrder() {
                return -100;
            }
        };

        CustomInjector defaultPriority = new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint ip) {
                return false;
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint ip) {
                return InjectionResult.notHandled();
            }
        };

        // register in random order
        registry.register(lowPriority);
        registry.register(defaultPriority);
        registry.register(highPriority);

        List<CustomInjector> sorted = registry.getInjectors();
        assertEquals(3, sorted.size());
        assertSame(highPriority, sorted.get(0));
        assertSame(defaultPriority, sorted.get(1));
        assertSame(lowPriority, sorted.get(2));
    }
}
