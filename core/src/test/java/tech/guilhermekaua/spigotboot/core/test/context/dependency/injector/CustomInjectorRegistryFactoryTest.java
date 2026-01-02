
package tech.guilhermekaua.spigotboot.core.test.context.dependency.injector;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.*;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CustomInjectorRegistryFactoryTest {
    private DependencyManager dependencyManager;
    private BeanDefinitionRegistry definitionRegistry;
    private CustomInjectorRegistry registry;
    private Context mockContext;
    private BeanRegistrar mockRegistrar;

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
        definitionRegistry = dependencyManager.getBeanDefinitionRegistry();
        registry = dependencyManager.getCustomInjectorRegistry();

        mockContext = mock(Context.class);
        mockRegistrar = mock(BeanRegistrar.class);

        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        when(mockContext.getDependencyManager()).thenReturn(dependencyManager);
        when(mockContext.getPlugin()).thenReturn(plugin);
    }

    @Test
    void testFactoryAppliesCustomizersInOrder() {
        List<String> callOrder = new ArrayList<>();

        CustomInjectorRegistryCustomizer first = new CustomInjectorRegistryCustomizer() {
            @Override
            public void customize(@NotNull CustomInjectorRegistry registry) {
                callOrder.add("first");
            }

            @Override
            public int getOrder() {
                return -10;
            }
        };

        CustomInjectorRegistryCustomizer second = new CustomInjectorRegistryCustomizer() {
            @Override
            public void customize(@NotNull CustomInjectorRegistry registry) {
                callOrder.add("second");
            }

            @Override
            public int getOrder() {
                return 10;
            }
        };

        dependencyManager.registerDependency(second, "second", false);
        dependencyManager.registerDependency(first, "first", false);

        CustomInjectorRegistryFactory factory = new CustomInjectorRegistryFactory();
        factory.onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar);

        assertEquals(List.of("first", "second"), callOrder);
    }

    @Test
    void testFactoryAppliesMultipleUnqualifiedCustomizers() {
        List<String> callOrder = new ArrayList<>();

        dependencyManager.registerDependency(
                CustomInjectorRegistryCustomizer.class,
                null,
                false,
                type -> new CustomInjectorRegistryCustomizer() {
                    @Override
                    public void customize(@NotNull CustomInjectorRegistry registry) {
                        callOrder.add("first");
                    }

                    @Override
                    public int getOrder() {
                        return -10;
                    }
                }
        );

        dependencyManager.registerDependency(
                CustomInjectorRegistryCustomizer.class,
                null,
                false,
                type -> new CustomInjectorRegistryCustomizer() {
                    @Override
                    public void customize(@NotNull CustomInjectorRegistry registry) {
                        callOrder.add("second");
                    }

                    @Override
                    public int getOrder() {
                        return 10;
                    }
                }
        );

        CustomInjectorRegistryFactory factory = new CustomInjectorRegistryFactory();
        factory.onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar);

        assertEquals(List.of("first", "second"), callOrder);
    }

    @Test
    void testFactoryResolvesCustomizerFromDefinitionAndAppliesIt() {

        CustomInjector injector = createTestInjector(0);

        dependencyManager.registerDependency(
                CustomInjectorRegistryCustomizer.class,
                "fromDefinition",
                false,
                (type) -> reg -> reg.register(injector),
                null
        );

        CustomInjectorRegistryFactory factory = new CustomInjectorRegistryFactory();
        factory.onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar);

        assertEquals(1, registry.size());
        assertSame(injector, registry.getInjectors().get(0));
    }

    @Test
    void testFactoryContinuesWhenCustomizerThrows() {
        CustomInjector injector = createTestInjector(0);

        CustomInjectorRegistryCustomizer failing = new CustomInjectorRegistryCustomizer() {
            @Override
            public void customize(@NotNull CustomInjectorRegistry registry) {
                throw new RuntimeException("boom");
            }

            @Override
            public int getOrder() {
                return -10;
            }
        };

        CustomInjectorRegistryCustomizer succeeding = new CustomInjectorRegistryCustomizer() {
            @Override
            public void customize(@NotNull CustomInjectorRegistry registry) {
                registry.register(injector);
            }

            @Override
            public int getOrder() {
                return 10;
            }
        };

        dependencyManager.registerDependency(failing, "failing", false);
        dependencyManager.registerDependency(succeeding, "succeeding", false);

        CustomInjectorRegistryFactory factory = new CustomInjectorRegistryFactory();

        assertDoesNotThrow(() -> factory.onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar));
        assertEquals(1, registry.size());
    }

    @Test
    void testFactoryContinuesWhenCustomizerResolutionFails() {
        CustomInjector injector = createTestInjector(0);

        dependencyManager.registerDependency(
                CustomInjectorRegistryCustomizer.class,
                "failsToResolve",
                false,
                (type) -> {
                    throw new RuntimeException("resolve boom");
                },
                null
        );

        dependencyManager.registerDependency(
                CustomInjectorRegistryCustomizer.class,
                "ok",
                false,
                (type) -> reg -> reg.register(injector),
                null
        );

        CustomInjectorRegistryFactory factory = new CustomInjectorRegistryFactory();

        assertDoesNotThrow(() -> factory.onBeanDefinitionsReady(mockContext, definitionRegistry, mockRegistrar));
        assertEquals(1, registry.size());
    }

    private static @NotNull CustomInjector createTestInjector(int order) {
        return new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint injectionPoint) {
                return false;
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
                return InjectionResult.notHandled();
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }
}
