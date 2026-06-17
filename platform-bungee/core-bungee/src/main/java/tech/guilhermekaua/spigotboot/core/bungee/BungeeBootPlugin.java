/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.core.bungee;

import lombok.EqualsAndHashCode;
import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Adapts a BungeeCord {@link Plugin} to the platform-neutral {@link BootPlugin} contract so the
 * Spigot Boot context can run on a proxy. Mirrors the Spigot {@code SpigotBootPlugin}.
 */
@EqualsAndHashCode(of = "plugin")
public class BungeeBootPlugin implements BootPlugin {
    private final Plugin plugin;

    public BungeeBootPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return plugin.getDescription().getName();
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public File getDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public InputStream getResource(String path) {
        return plugin.getResourceAsStream(path);
    }

    @Override
    public ClassLoader getClassLoader() {
        return getMainClass().getClassLoader();
    }

    @Override
    public Class<?> getMainClass() {
        return ProxyUtils.getRealClass(plugin);
    }

    @Override
    public Object getNativePlugin() {
        return plugin;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}
