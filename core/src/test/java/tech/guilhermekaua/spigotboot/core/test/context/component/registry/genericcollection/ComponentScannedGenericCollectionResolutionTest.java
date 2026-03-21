package tech.guilhermekaua.spigotboot.core.test.context.component.registry.genericcollection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ComponentScannedGenericCollectionResolutionTest {

    private static final String TEST_PACKAGE =
            "tech.guilhermekaua.spigotboot.core.test.context.component.registry.genericcollection";

    private DependencyManager dependencyManager;
    private ComponentRegistry componentRegistry;
    private ConfigurationProcessor configurationProcessor;

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
        componentRegistry = new ComponentRegistry();
        configurationProcessor = new ConfigurationProcessor();
    }

    @Test
    void componentScan_injectsParameterizedInterfaceCollectionsIntoScannedComponents() {
        componentRegistry.registerComponents(TEST_PACKAGE, dependencyManager);

        GenericExtensionCollector collector = dependencyManager.resolveDependency(GenericExtensionCollector.class, null);

        assertNotNull(collector);
        assertNotNull(collector.getExtensions());
        assertEquals(2, collector.getExtensions().size());
        assertTrue(collector.getExtensions().stream().anyMatch(extension -> "string".equals(extension.id())));
        assertTrue(collector.getExtensions().stream().anyMatch(extension -> "integer".equals(extension.id())));
    }

    @Test
    void beanMethodParameters_resolveParameterizedInterfaceCollectionsFromScannedComponents() {
        componentRegistry.registerComponents(TEST_PACKAGE, dependencyManager);
        configurationProcessor.processFromPackage(TEST_PACKAGE, dependencyManager);

        GenericExtensionSummary summary = dependencyManager.resolveDependency(GenericExtensionSummary.class, null);

        assertNotNull(summary);
        assertNotNull(summary.getExtensions());
        assertEquals(2, summary.getExtensions().size());
        assertTrue(summary.getExtensions().stream().anyMatch(extension -> "string".equals(extension.id())));
        assertTrue(summary.getExtensions().stream().anyMatch(extension -> "integer".equals(extension.id())));
    }
}

interface GenericExtension<T> {
    String id();
}

@Component
class StringGenericExtension implements GenericExtension<String> {
    @Override
    public String id() {
        return "string";
    }
}

@Component
class IntegerGenericExtension implements GenericExtension<Integer> {
    @Override
    public String id() {
        return "integer";
    }
}

@Component
class GenericExtensionCollector {
    @Inject
    private List<GenericExtension<?>> extensions;

    List<GenericExtension<?>> getExtensions() {
        return extensions;
    }
}

@Configuration
class GenericExtensionConfiguration {
    @Bean
    GenericExtensionSummary genericExtensionSummary(List<GenericExtension<?>> extensions) {
        return new GenericExtensionSummary(extensions);
    }
}

class GenericExtensionSummary {
    private final List<GenericExtension<?>> extensions;

    GenericExtensionSummary(List<GenericExtension<?>> extensions) {
        this.extensions = extensions;
    }

    List<GenericExtension<?>> getExtensions() {
        return extensions;
    }
}
