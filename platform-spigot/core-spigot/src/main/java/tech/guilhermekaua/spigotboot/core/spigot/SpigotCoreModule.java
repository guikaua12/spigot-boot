package tech.guilhermekaua.spigotboot.core.spigot;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

@Order(-1000)
public class SpigotCoreModule implements Module {

    @Override
    public void onInitialize(Context context) throws Exception {
        BootPlugin bootPlugin = context.getPlugin();
        Object nativePlugin = bootPlugin.getNativePlugin();
        DependencyManager dependencyManager = context.getDependencyManager();

        dependencyManager.registerDependency(Plugin.class, (Plugin) nativePlugin, null, false);
        dependencyManager.registerDependency(JavaPlugin.class, (JavaPlugin) nativePlugin, null, false);
        dependencyManager.registerDependency(
                ProxyUtils.getRealClass(nativePlugin),
                nativePlugin,
                null,
                false
        );
    }
}
