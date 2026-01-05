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
package tech.guilhermekaua.spigotboot.config.collection;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Utility for computing semantic hashes of config nodes.
 * <p>
 * This class provides a way to detect YAML changes while ignoring
 * map key order. Lists are order-sensitive, but maps are not.
 * <p>
 * The hash uses SHA-256 of a canonical form where:
 * <ul>
 *   <li>Map keys are sorted lexicographically</li>
 *   <li>List items preserve order</li>
 *   <li>Scalars are prefixed with a type tag</li>
 * </ul>
 */
public final class ConfigNodeHash {

    private ConfigNodeHash() {
    }

    /**
     * Computes an SHA-256 hash of the config node's canonical form.
     *
     * @param node the config node to hash
     * @return the hex-encoded SHA-256 hash
     */
    public static @NotNull String sha256(@NotNull ConfigNode node) {
        Objects.requireNonNull(node, "node cannot be null");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = canonicalize(node);
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Creates a canonical string representation of the node.
     * <p>
     * This representation ignores map key order but preserves list order.
     *
     * @param node the config node
     * @return the canonical string
     */
    public static @NotNull String canonicalize(@NotNull ConfigNode node) {
        Objects.requireNonNull(node, "node cannot be null");

        StringBuilder sb = new StringBuilder();
        appendCanonical(node, sb);
        return sb.toString();
    }

    private static void appendCanonical(@NotNull ConfigNode node, @NotNull StringBuilder sb) {
        if (node.isNull() || node.isVirtual()) {
            sb.append("N:");
        } else if (node.isMap()) {
            sb.append("M{");
            Map<String, ? extends ConfigNode> children = node.childrenMap();
            List<String> keys = new ArrayList<>(children.keySet());
            Collections.sort(keys);

            boolean first = true;
            for (String key : keys) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append(escapeKey(key)).append(":");
                appendCanonical(children.get(key), sb);
            }
            sb.append("}");
        } else if (node.isList()) {
            sb.append("L[");
            List<? extends ConfigNode> children = node.childrenList();
            boolean first = true;
            for (ConfigNode child : children) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                appendCanonical(child, sb);
            }
            sb.append("]");
        } else {
            // scalar
            Object raw = node.raw();
            if (raw == null) {
                sb.append("N:");
            } else if (raw instanceof String) {
                sb.append("S:").append(escapeScalar(raw.toString()));
            } else if (raw instanceof Boolean) {
                sb.append("B:").append(raw);
            } else if (raw instanceof Integer || raw instanceof Long) {
                sb.append("I:").append(raw);
            } else if (raw instanceof Float || raw instanceof Double) {
                sb.append("D:").append(raw);
            } else {
                sb.append("O:").append(escapeScalar(String.valueOf(raw)));
            }
        }
    }

    private static String escapeKey(@NotNull String key) {
        return key.replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace(",", "\\,")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    private static String escapeScalar(@NotNull String value) {
        return value.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
