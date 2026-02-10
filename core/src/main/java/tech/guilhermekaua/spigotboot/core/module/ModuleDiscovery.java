package tech.guilhermekaua.spigotboot.core.module;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class ModuleDiscovery {
    private static final Logger LOGGER = Logger.getLogger(ModuleDiscovery.class.getName());
    private static final String MODULES_PATH = "META-INF/spigot-boot/modules";
    private static final String MODULES_PATH_PREFIX = MODULES_PATH + "/";
    private static final String DISCOVERY_PROPERTIES_PATH = "META-INF/spigot-boot/discovery.properties";
    private static final String PACKAGE_SUFFIX = ".core.module";

    private final ClassLoader classLoader;

    public ModuleDiscovery(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
    }

    @SuppressWarnings("unchecked")
    public List<Class<? extends Module>> discover() {
        Set<String> discoveredFqcns = new LinkedHashSet<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(MODULES_PATH);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("jar".equals(protocol)) {
                    discoverFromJar(resource, discoveredFqcns);
                } else if ("file".equals(protocol)) {
                    discoverFromDirectory(resource, discoveredFqcns);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan for module descriptors", e);
        }

        Set<String> resolvedFqcns = resolveRelocatedFqcns(discoveredFqcns);

        List<Class<? extends Module>> modules = new ArrayList<>();
        for (String fqcn : resolvedFqcns) {
            try {
                Class<?> clazz = Class.forName(fqcn, false, classLoader);
                if (!Module.class.isAssignableFrom(clazz)) {
                    throw new IllegalStateException(
                            "Class '" + fqcn + "' declared in " + MODULES_PATH_PREFIX +
                                    " does not implement " + Module.class.getName()
                    );
                }
                modules.add((Class<? extends Module>) clazz);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                LOGGER.log(Level.WARNING, "Skipping module '" + fqcn + "': class not found on classpath", e);
            }
        }

        return modules;
    }

    private Set<String> resolveRelocatedFqcns(Set<String> originalFqcns) {
        String originalBase = readOriginalBasePackage();
        if (originalBase == null) {
            return originalFqcns;
        }

        String currentBase = detectCurrentBasePackage();
        if (currentBase == null) {
            return originalFqcns;
        }

        if (originalBase.equals(currentBase)) {
            return originalFqcns;
        }

        LOGGER.log(Level.INFO, "Relocation detected: ''{0}'' -> ''{1}''. Rewriting module FQCNs.",
                new Object[]{originalBase, currentBase});

        Set<String> relocated = new LinkedHashSet<>();
        for (String fqcn : originalFqcns) {
            relocated.add(resolveRelocatedFqcn(fqcn, originalBase, currentBase));
        }
        return relocated;
    }

    private String resolveRelocatedFqcn(String fqcn, String originalBase, String currentBase) {
        if (fqcn.startsWith(originalBase)) {
            return currentBase + fqcn.substring(originalBase.length());
        }
        return fqcn;
    }

    private String readOriginalBasePackage() {
        try (InputStream is = classLoader.getResourceAsStream(DISCOVERY_PROPERTIES_PATH)) {
            if (is == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(is);
            String value = props.getProperty("original-base-package");
            return value != null ? value.trim() : null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read " + DISCOVERY_PROPERTIES_PATH, e);
            return null;
        }
    }

    private String detectCurrentBasePackage() {
        String currentPackage = ModuleDiscovery.class.getPackage().getName();

        if (!currentPackage.endsWith(PACKAGE_SUFFIX)) {
            LOGGER.log(Level.WARNING, "ModuleDiscovery package ''{0}'' does not end with expected suffix ''{1}''. " +
                    "Cannot detect relocation.", new Object[]{currentPackage, PACKAGE_SUFFIX});
            return null;
        }
        return currentPackage.substring(0, currentPackage.length() - PACKAGE_SUFFIX.length());
    }

    private void discoverFromJar(URL resource, Set<String> fqcns) {
        try {
            JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
            jarConnection.setUseCaches(false);

            try (JarFile jarFile = jarConnection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    if (entry.isDirectory()) {
                        continue;
                    }

                    if (!entryName.startsWith(MODULES_PATH_PREFIX)) {
                        continue;
                    }

                    String fqcn = entryName.substring(MODULES_PATH_PREFIX.length());
                    if (!fqcn.isEmpty() && !fqcn.contains("/")) {
                        fqcns.add(fqcn);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan JAR for module descriptors: " + resource, e);
        }
    }

    private void discoverFromDirectory(URL resource, Set<String> fqcns) {
        try {
            Path directory = Paths.get(resource.toURI());
            if (!Files.isDirectory(directory)) {
                return;
            }

            try (Stream<Path> paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> !name.isEmpty())
                        .forEach(fqcns::add);
            }
        } catch (URISyntaxException | IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan directory for module descriptors: " + resource, e);
        }
    }
}
