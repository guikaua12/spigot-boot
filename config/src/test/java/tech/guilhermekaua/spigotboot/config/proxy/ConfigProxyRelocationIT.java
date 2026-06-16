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
package tech.guilhermekaua.spigotboot.config.proxy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the shaded-javassist NoClassDefFoundError. {@code ConfigProxy} uses the
 * javassist proxy API directly, so its pre-shade bytecode legitimately references {@code javassist/};
 * only the PACKAGED (shaded) jar must be clean. A normal surefire test runs against the un-shaded
 * {@code target/classes} and cannot catch this — so this is a failsafe {@code *IT} bound to the
 * {@code verify} phase (after {@code package}/shade), inspecting the produced jar.
 */
class ConfigProxyRelocationIT {

    private static final String SHADED_PREFIX = "tech/guilhermekaua/spigotboot/shaded/javassist/";
    private static final String CONFIG_PROXY_ENTRY =
            "tech/guilhermekaua/spigotboot/config/proxy/ConfigProxy.class";

    @Test
    void packagedConfigProxyReferencesOnlyShadedJavassist() throws IOException {
        String buildDir = System.getProperty("project.build.directory");
        String finalName = System.getProperty("project.build.finalName");
        assertNotNull(buildDir, "project.build.directory must be supplied by the failsafe plugin");
        assertNotNull(finalName, "project.build.finalName must be supplied by the failsafe plugin");

        Path jar = Path.of(buildDir, finalName + ".jar");
        assertTrue(Files.exists(jar),
                "shaded jar not found at " + jar + " — run the package/verify phase, not just test");

        byte[] bytes;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(CONFIG_PROXY_ENTRY);
            assertNotNull(entry, CONFIG_PROXY_ENTRY + " not found inside " + jar);
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = in.readAllBytes();
            }
        }

        String pool = new String(bytes, StandardCharsets.ISO_8859_1);

        assertTrue(pool.contains(SHADED_PREFIX),
                "ConfigProxy.class must reference the relocated javassist package " + SHADED_PREFIX
                        + " in the packaged jar (relocation did not run)");

        // strip the shaded prefix first: the relocated form itself contains "javassist/".
        String withoutShaded = pool.replace(SHADED_PREFIX, "");
        assertFalse(withoutShaded.contains("javassist/"),
                "ConfigProxy.class still references the un-relocated javassist package in the packaged jar");
    }
}
