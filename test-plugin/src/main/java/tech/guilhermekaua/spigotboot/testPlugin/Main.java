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
package tech.guilhermekaua.spigotboot.testPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.annotationprocessor.annotations.Plugin;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigModule;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.data.ormLite.DataOrmLiteModule;
import tech.guilhermekaua.spigotboot.placeholder.PlaceholderModule;
import tech.guilhermekaua.spigotboot.testPlugin.configuration.MainConfig;

@Plugin(
        name = "TestPlugin",
        version = "1.0.0",
        description = "A test plugin for ApxPlugin framework.",
        authors = {"Approximations"}
)
public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        Context ctx = SpigotBoot.initialize(this,
                DataOrmLiteModule.class,
                PlaceholderModule.class,
                SpigotConfigModule.class
        );

        MainConfig mainConfig = ctx.getBean(MainConfig.class);
        getLogger().info("MainConfig - Server name: " + mainConfig.getServerName() + ". Max players: " + mainConfig.getMaxPlayers());
    }

    @Override
    public void onDisable() {
        SpigotBoot.onDisable(this);
    }
}