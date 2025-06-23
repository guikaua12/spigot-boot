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
package tech.guilhermekaua.spigotboot.core.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ConfigManager {

    private YamlConfiguration yml;
    private File config;
    private String name;
    private boolean firstTime = false;

    /**
     * Create a new configuration file.
     *
     * @param plugin config owner.
     * @param name   config name. Do not include .yml in it.
     */
    public ConfigManager(Plugin plugin, String name, String dir) {
        final File d = new File(dir);
        if (!d.exists()) {
            if (!d.mkdirs()) {
                plugin.getLogger().log(Level.SEVERE, "Could not create " + d.getPath());
                return;
            }
        }

        config = new File(dir, name + ".yml");
        if (!config.exists()) {
            firstTime = true;
            plugin.getLogger().log(Level.INFO, "Creating " + config.getPath());
            try {
                if (!config.createNewFile()) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create " + config.getPath());
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        yml = YamlConfiguration.loadConfiguration(config);
        yml.options().copyDefaults(true);
        this.name = name;
    }

    /**
     * Reload configuration.
     */
    public void reload() {
        yml = YamlConfiguration.loadConfiguration(config);
    }

    /**
     * Convert a location to an arena location syntax
     */
    public String stringLocationArenaFormat(Location loc) {
        return loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + (double) loc.getYaw() + "," + (double) loc.getPitch();
    }

    /**
     * Convert a location to a string for general use
     * Use {@link #stringLocationArenaFormat(Location)} for arena locations
     */
    public String stringLocationConfigFormat(Location loc) {
        return loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + (double) loc.getYaw() + "," + (double) loc.getPitch() + "," + loc.getWorld().getName();
    }

    /**
     * Save a general location to the config.
     * Use {@link #saveArenaLoc(String, Location)} for arena locations
     */
    public void saveConfigLoc(String path, Location loc) {
        final String data = loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + (double) loc.getYaw() + "," + (double) loc.getPitch() + "," + loc.getWorld().getName();
        yml.set(path, data);
        save();
    }

    /**
     * Save a location for arena use
     */
    public void saveArenaLoc(String path, Location loc) {
        final String data = loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + (double) loc.getYaw() + "," + (double) loc.getPitch();
        yml.set(path, data);
        save();
    }

    /**
     * Get a general location
     * Use {@link #getArenaLoc(String)} for locations stored using {@link #saveArenaLoc(String, Location)}
     */
    public Location getConfigLoc(String path) {
        final String d = yml.getString(path);
        if (d == null) return null;
        final String[] data = d.replace("[", "").replace("]", "").split(",");
        return new Location(Bukkit.getWorld(data[5]), Double.parseDouble(data[0]), Double.parseDouble(data[1]), Double.parseDouble(data[2]), Float.parseFloat(data[3]), Float.parseFloat(data[4]));
    }

    /**
     * Get a location for arena use
     * Use {@link #getConfigLoc(String)} (String)} for locations stored using {@link #saveConfigLoc(String, Location)} (String, Location)}
     */
    public Location getArenaLoc(String path) {
        final String d = yml.getString(path);
        if (d == null) return null;
        final String[] data = d.replace("[", "").replace("]", "").split(",");
        return new Location(Bukkit.getWorld(name), Double.parseDouble(data[0]), Double.parseDouble(data[1]), Double.parseDouble(data[2]), Float.parseFloat(data[3]), Float.parseFloat(data[4]));
    }

    /**
     * Convert string to arena location syntax
     */
    public Location convertStringToArenaLocation(String string) {
        final String[] data = string.split(",");
        return new Location(Bukkit.getWorld(name), Double.parseDouble(data[0]), Double.parseDouble(data[1]), Double.parseDouble(data[2]), Float.parseFloat(data[3]), Float.parseFloat(data[4]));

    }

    /**
     * Get list of arena locations at given path
     */
    public List<Location> getArenaLocations(String path) {
        final List<Location> l = new ArrayList<>();
        for (String s : yml.getStringList(path)) {
            final Location loc = convertStringToArenaLocation(s);
            if (loc != null) {
                l.add(loc);
            }
        }
        return l;
    }

    /**
     * Set data to config
     */
    public void set(String path, Object value) {
        yml.set(path, value);
        save();
    }

    /**
     * Get yml instance
     */
    public YamlConfiguration getYml() {
        return yml;
    }

    /**
     * Save config changes to file
     */
    public void save() {
        try {
            yml.save(config);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Get list of strings at given path
     *
     * @return a list of string with colors translated
     */
    public List<String> getList(String path) {
        return yml.getStringList(path).stream().map(s -> s.replace("&", "§")).collect(Collectors.toList());
    }

    /**
     * Get boolean at given path
     */
    public boolean getBoolean(String path) {
        return yml.getBoolean(path);
    }

    /**
     * Get Integer at given path
     */
    public int getInt(String path) {
        return yml.getInt(path);
    }

    /**
     * Get long at given path
     */
    public long getLong(String path) {
        return yml.getLong(path);
    }

    public double getDouble(String path) {
        return yml.getDouble(path);
    }


    /**
     * Get string at given path
     */
    public String getString(String path) {
        return ChatColor.translateAlternateColorCodes('&', yml.getString(path));
    }

    /**
     * Check if the config file was created for the first time
     * Can be used to add default values
     */
    public boolean isFirstTime() {
        return firstTime;
    }

    /**
     * Compare two arena locations
     * Return true if same location
     */
    public boolean compareArenaLoc(Location l1, Location l2) {
        return l1.getBlockX() == l2.getBlockX() && l1.getBlockZ() == l2.getBlockZ() && l1.getBlockY() == l2.getBlockY();
    }

    /**
     * Get config name
     */
    public String getName() {
        return name;
    }

    /**
     * Change internal name.
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getFinalPath(String... path) {
        final StringJoiner stringJoiner = new StringJoiner(".");
        for (final String s : path) {
            stringJoiner.add(s);
        }
        return stringJoiner.toString();
    }
}