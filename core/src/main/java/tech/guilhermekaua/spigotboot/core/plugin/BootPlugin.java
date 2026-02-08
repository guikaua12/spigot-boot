package tech.guilhermekaua.spigotboot.core.plugin;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

public interface BootPlugin {
    String getName();

    Logger getLogger();

    File getDataFolder();

    InputStream getResource(String path);

    ClassLoader getClassLoader();

    Class<?> getMainClass();

    Object getNativePlugin();
}
