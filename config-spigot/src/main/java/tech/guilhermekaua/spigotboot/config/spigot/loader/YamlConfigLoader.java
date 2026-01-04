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
package tech.guilhermekaua.spigotboot.config.spigot.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.loader.ConfigLoader;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.spigot.node.YamlConfigNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * YAML configuration loader using SnakeYAML (bundled with Bukkit).
 */
public class YamlConfigLoader implements ConfigLoader {

    private static final String[] EXTENSIONS = {"yml", "yaml"};

    private final Yaml yaml;

    public YamlConfigLoader() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        this.yaml = new Yaml(options);
    }

    @Override
    public @NotNull ConfigNode load(@NotNull ConfigSource source) throws ConfigException {
        if (!source.exists()) {
            return new YamlConfigNode();
        }

        try (InputStream is = source.openRead();
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            Object data = yaml.load(reader);
            if (data == null) {
                return new YamlConfigNode();
            }
            return new YamlConfigNode(data);

        } catch (IOException e) {
            throw new ConfigException("Failed to load config from " + source.name(), e);
        } catch (Exception e) {
            throw new ConfigException("Failed to parse YAML from " + source.name(), e);
        }
    }

    @Override
    public void save(@NotNull ConfigNode node, @NotNull ConfigSource target) throws ConfigException {
        Path targetPath = target.path();
        if (targetPath == null) {
            throw new ConfigException("Cannot save to non-file source: " + target.name());
        }

        try {
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Path tempFile = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");

            try (OutputStream os = Files.newOutputStream(tempFile);
                 Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {

                if (node instanceof YamlConfigNode) {
                    YamlConfigNode yamlNode = (YamlConfigNode) node;
                    writeNodeWithComments(writer, yamlNode, 0);
                } else {
                    Object rawData = node.raw();
                    if (rawData instanceof Map || rawData instanceof List) {
                        yaml.dump(rawData, writer);
                    } else if (rawData != null) {
                        writer.write(String.valueOf(rawData));
                    }
                }
            }

            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new ConfigException("Failed to save config to " + target.name(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void writeNodeWithComments(@NotNull Writer writer, @NotNull YamlConfigNode node, int indent) throws IOException {
        Object value = node.raw();
        if (!(value instanceof Map)) {
            if (value != null) {
                yaml.dump(value, writer);
            }
            return;
        }

        Map<String, Object> map = (Map<String, Object>) value;
        String indentStr = repeat(" ", indent);

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            List<String> comments = node.getChildComments(key);

            if (comments != null && !comments.isEmpty()) {
                for (String line : comments) {
                    writer.write(indentStr);
                    writer.write("# ");
                    writer.write(line);
                    writer.write("\n");
                }
            }

            writer.write(indentStr);
            writer.write(key);
            writer.write(":");

            if (val instanceof Map) {
                writer.write("\n");
                YamlConfigNode childNode = (YamlConfigNode) node.node(key);
                writeNodeWithComments(writer, childNode, indent + 2);

            } else if (val instanceof List) {
                writer.write("\n");
                writeList(writer, (List<?>) val, indent + 2);
            } else {
                writer.write(" ");
                writeScalar(writer, val);
                writer.write("\n");
            }
        }
    }

    private void writeList(@NotNull Writer writer, @NotNull List<?> list, int indent) throws IOException {
        String indentStr = repeat(" ", indent);
        for (Object item : list) {
            writer.write(indentStr);
            writer.write("- ");
            if (item instanceof Map) {
                writer.write("\n");
                writeMapInList(writer, (Map<?, ?>) item, indent + 2);
            } else if (item instanceof List) {
                writer.write("\n");
                writeList(writer, (List<?>) item, indent + 2);
            } else {
                writeScalar(writer, item);
                writer.write("\n");
            }
        }
    }

    private void writeMapInList(@NotNull Writer writer, @NotNull Map<?, ?> map, int indent) throws IOException {
        String indentStr = repeat(" ", indent);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object val = entry.getValue();

            writer.write(indentStr);
            writer.write(key);
            writer.write(":");

            if (val instanceof Map) {
                writer.write("\n");
                writeMapInList(writer, (Map<?, ?>) val, indent + 2);
            } else if (val instanceof List) {
                writer.write("\n");
                writeList(writer, (List<?>) val, indent + 2);
            } else {
                writer.write(" ");
                writeScalar(writer, val);
                writer.write("\n");
            }
        }
    }

    private void writeScalar(@NotNull Writer writer, @Nullable Object value) throws IOException {
        if (value == null) {
            writer.write("null");
        } else if (value instanceof String) {
            String str = (String) value;
            if (needsQuoting(str)) {
                writer.write("\"");
                writer.write(escapeString(str));
                writer.write("\"");
            } else {
                writer.write(str);
            }
        } else if (value instanceof Boolean || value instanceof Number) {
            writer.write(String.valueOf(value));
        } else {
            String str = String.valueOf(value);
            if (needsQuoting(str)) {
                writer.write("\"");
                writer.write(escapeString(str));
                writer.write("\"");
            } else {
                writer.write(str);
            }
        }
    }

    private boolean needsQuoting(@NotNull String str) {
        if (str.isEmpty()) {
            return true;
        }
        char first = str.charAt(0);

        if (first == '#' || first == '&' || first == '*' || first == '!' ||
                first == '|' || first == '>' || first == '\'' || first == '"' ||
                first == '%' || first == '@' || first == '`') {
            return true;
        }
        if (str.contains(": ") || str.contains("\n") || str.contains("\r")) {
            return true;
        }
        String lower = str.toLowerCase();

        if (lower.equals("true") || lower.equals("false") ||
                lower.equals("yes") || lower.equals("no") ||
                lower.equals("null") || lower.equals("~")) {
            return true;
        }
        return false;
    }

    private String escapeString(@NotNull String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String repeat(@NotNull String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    @Override
    public @NotNull MutableConfigNode createNode() {
        return new YamlConfigNode();
    }

    @Override
    public @NotNull String[] extensions() {
        return EXTENSIONS;
    }
}
