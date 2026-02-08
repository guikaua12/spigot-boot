package tech.guilhermekaua.spigotboot.core.spigot;

import lombok.EqualsAndHashCode;
import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

@EqualsAndHashCode(of = "javaPlugin")
public class SpigotBootPlugin implements BootPlugin {
    private final JavaPlugin javaPlugin;

    public SpigotBootPlugin(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
    }

    @Override
    public String getName() {
        return javaPlugin.getName();
    }

    @Override
    public Logger getLogger() {
        return javaPlugin.getLogger();
    }

    @Override
    public File getDataFolder() {
        return javaPlugin.getDataFolder();
    }

    @Override
    public InputStream getResource(String path) {
        return javaPlugin.getResource(path);
    }

    @Override
    public ClassLoader getClassLoader() {
        return getMainClass().getClassLoader();
    }

    @Override
    public Class<?> getMainClass() {
        return ProxyUtils.getRealClass(javaPlugin);
    }

    @Override
    public Object getNativePlugin() {
        return javaPlugin;
    }

    public JavaPlugin getJavaPlugin() {
        return javaPlugin;
    }
}
