package tech.guilhermekaua.spigotboot.core;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleDiscovery;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class SpigotBootBuilder {
    private final BootPlugin plugin;
    private boolean autoDiscover = false;
    private final List<ModuleEntry> moduleEntries = new ArrayList<>();
    private final Set<Class<? extends Module>> excludeModules = new LinkedHashSet<>();

    SpigotBootBuilder(BootPlugin plugin) {
        this.plugin = plugin;
    }

    public SpigotBootBuilder autoDiscover() {
        this.autoDiscover = true;
        return this;
    }

    @SafeVarargs
    public final SpigotBootBuilder exclude(Class<? extends Module>... modules) {
        Collections.addAll(excludeModules, modules);
        return this;
    }

    public SpigotBootBuilder module(Class<? extends Module> moduleClass, int order) {
        moduleEntries.add(new ModuleEntry(moduleClass, order));
        return this;
    }

    public SpigotBootBuilder module(Class<? extends Module> moduleClass) {
        moduleEntries.add(new ModuleEntry(moduleClass, null));
        return this;
    }

    @SafeVarargs
    public final SpigotBootBuilder modules(Class<? extends Module>... modules) {
        for (Class<? extends Module> mc : modules) {
            moduleEntries.add(new ModuleEntry(mc, null));
        }
        return this;
    }

    List<Class<? extends Module>> resolveModules() {
        List<ModuleEntry> entries;

        if (autoDiscover) {
            List<Class<? extends Module>> discovered = new ModuleDiscovery(plugin.getClassLoader()).discover();
            entries = new ArrayList<>();
            for (Class<? extends Module> dc : discovered) {
                entries.add(new ModuleEntry(dc, null));
            }

            for (ModuleEntry explicit : moduleEntries) {
                boolean found = false;
                for (int i = 0; i < entries.size(); i++) {
                    if (entries.get(i).moduleClass == explicit.moduleClass) {
                        entries.set(i, explicit);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    entries.add(explicit);
                }
            }

            entries.removeIf(e -> excludeModules.contains(e.moduleClass));
        } else if (!moduleEntries.isEmpty()) {
            LinkedHashMap<Class<? extends Module>, ModuleEntry> map = new LinkedHashMap<>();
            for (ModuleEntry entry : moduleEntries) {
                if (!excludeModules.contains(entry.moduleClass)) {
                    map.put(entry.moduleClass, entry);
                }
            }
            entries = new ArrayList<>(map.values());
        } else {
            entries = Collections.emptyList();
        }

        entries.sort(Comparator.comparingInt(ModuleEntry::getEffectiveOrder));
        return entries.stream()
                .map(e -> e.moduleClass)
                .collect(Collectors.toList());
    }

    public Context initialize() {
        return SpigotBoot.doInitialize(plugin, resolveModules());
    }

    static class ModuleEntry {
        final Class<? extends Module> moduleClass;
        final Integer explicitOrder;

        ModuleEntry(Class<? extends Module> moduleClass, Integer explicitOrder) {
            this.moduleClass = moduleClass;
            this.explicitOrder = explicitOrder;
        }

        int getEffectiveOrder() {
            if (explicitOrder != null) return explicitOrder;
            Order order = moduleClass.getAnnotation(Order.class);
            return order != null ? order.value() : 0;
        }
    }
}
