package tech.guilhermekaua.spigotboot.core.test.context.dependency.registry;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BeanDefinitionRegistryTest {

    interface Service {
    }

    interface OtherService {
    }

    static class ServiceImpl implements Service, OtherService {
    }

    @Test
    void registerAndRetrieveDefinitions() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();

        BeanDefinition definition = new BeanDefinition(ServiceImpl.class, null, false, null, null);
        registry.register(Service.class, definition);

        List<BeanDefinition> definitions = registry.getDefinitions(Service.class);
        assertEquals(1, definitions.size());
        assertSame(definition, definitions.get(0));
        assertTrue(registry.getRegisteredTypes().contains(Service.class));
    }

    @Test
    void shouldRejectDuplicateDefinitionsForSameRequestedTypeAndQualifier() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();

        registry.register(Service.class, new BeanDefinition(ServiceImpl.class, null, false, null, null));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                registry.register(Service.class, new BeanDefinition(ServiceImpl.class, null, false, null, null))
        );

        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    void shouldAllowSameImplementationWithDifferentQualifier() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();

        registry.register(Service.class, new BeanDefinition(ServiceImpl.class, null, false, null, null));

        assertDoesNotThrow(() ->
                registry.register(Service.class, new BeanDefinition(ServiceImpl.class, "q", false, null, null))
        );
    }

    @Test
    void shouldAllowSameDefinitionUnderDifferentRequestedTypes() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();

        BeanDefinition definition = new BeanDefinition(ServiceImpl.class, null, false, null, null);
        registry.register(Service.class, definition);

        assertDoesNotThrow(() -> registry.register(OtherService.class, definition));
    }

    @Test
    void asMapViewShouldBeUnmodifiable() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();
        BeanDefinition definition = new BeanDefinition(ServiceImpl.class, null, false, null, null);
        registry.register(Service.class, definition);

        Map<Class<?>, List<BeanDefinition>> view = registry.asMapView();

        assertThrows(UnsupportedOperationException.class, () -> view.put(Service.class, new ArrayList<>()));
        assertThrows(UnsupportedOperationException.class, () -> view.get(Service.class).add(definition));
    }

    @Test
    void streamEntriesShouldFlattenAllDefinitions() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();
        registry.register(Service.class, new BeanDefinition(ServiceImpl.class, null, false, null, null));
        registry.register(OtherService.class, new BeanDefinition(ServiceImpl.class, "q", false, null, null));

        long count = registry.streamEntries().count();
        assertEquals(2, count);
    }
}


